package com.alisa.user.tenant;

import com.alisa.user.dto.user.UserDto;
import com.alisa.user.mapper.UserMapper;
import com.alisa.user.service.IdentityService;
import com.alisa.user.service.VerificationCodeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

public class OrganizationDirectory {
    private final JdbcTemplate jdbc;
    private final UserMapper users;
    private final IdentityService identity;
    private final TenantDirectory tenants;
    private final VerificationCodeService codes;

    public OrganizationDirectory(JdbcTemplate jdbc, UserMapper users, IdentityService identity, TenantDirectory tenants, VerificationCodeService codes) {
        this.jdbc = jdbc;
        this.users = users;
        this.identity = identity;
        this.tenants = tenants;
        this.codes = codes;
    }

    public record Organization(String id, String name) {}
    public record Account(String id, String username, String email, String userId, Timestamp expiresAt, String status, Timestamp restoreUntil) {}
    public enum AccountAction { RESET_PASSWORD, SUSPEND, RESUME, DELETE, RESTORE }
    private static final Duration RESTORE_WINDOW = Duration.ofDays(30);

    public record Activation(String id, String token) {}
    public record LinkedTenant(String id, String name) {}

    public String activeSessionVersion(String userId) {
        var versions = jdbc.query("select account_status,session_version from alisa_organization_account where user_id=?",
            (rs, row) -> {
                if (!"ACTIVE".equals(rs.getString(1))) throw new ResponseStatusException(UNAUTHORIZED, "账号已停用");
                return rs.getString(2);
            }, userId);
        return versions.isEmpty() ? "0" : versions.get(0);
    }

    public List<Organization> list(String actor) {
        return jdbc.query("select id,name from alisa_organization where owner_id=? order by name,id",
            (rs, row) -> new Organization(rs.getString(1), rs.getString(2)), actor);
    }

    public Organization create(String actor, String name) {
        if (users.findUserByID(actor) == null) throw new ResponseStatusException(UNAUTHORIZED);
        if (name == null || name.isBlank() || name.strip().length() > 100)
            throw new IllegalArgumentException("请输入 1 到 100 个字符的组织名称");
        var organization = new Organization(UUID.randomUUID().toString(), name.strip());
        jdbc.update("insert into alisa_organization(id,name,owner_id) values(?,?,?)", organization.id(), organization.name(), actor);
        return organization;
    }

    private void owner(String organization, String actor, boolean lock) {
        String sql = "select id from alisa_organization where id=? and owner_id=?";
        if (jdbc.queryForList(lock ? sql + " for update" : sql, String.class, organization, actor).isEmpty())
            throw new ResponseStatusException(FORBIDDEN, "需要组织所有者权限");
    }

    public List<Account> accounts(String organization, String actor) {
        owner(organization, actor, false);
        return jdbc.query("select id,username,email,user_id,expires_at,account_status,deleted_at from alisa_organization_account where organization_id=? order by username,id",
            (rs, row) -> {
                var deleted = rs.getTimestamp(7);
                String user = rs.getString(4);
                return new Account(rs.getString(1), rs.getString(2), rs.getString(3), user, rs.getTimestamp(5),
                    user == null ? "PENDING" : rs.getString(6),
                    deleted == null ? null : Timestamp.from(deleted.toInstant().plus(RESTORE_WINDOW)));
            }, organization);
    }

    @Transactional
    public Activation issue(String organization, String actor, String username, String email) {
        owner(organization, actor, true);
        username = username == null ? "" : username.strip();
        email = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (username.isBlank() || username.length() > 45 || email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("请填写有效的用户名和邮箱");
        if (users.findUserByName(username) != null || users.findUserByEmail(email) != null)
            throw new ResponseStatusException(CONFLICT, "账号已存在，请使用租户成员邀请");
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("delete from alisa_organization_account where organization_id=? and user_id is null and expires_at<=? and (username=? or email=?)",
            organization, now, username, email);
        if (!jdbc.queryForList("select id from alisa_organization_account where organization_id=? and user_id is null and (username=? or email=?)",
            String.class, organization, username, email).isEmpty())
            throw new ResponseStatusException(CONFLICT, "已有待激活账号，请先撤销旧链接");
        var activation = new Activation(UUID.randomUUID().toString(), UUID.randomUUID() + "." + UUID.randomUUID());
        jdbc.update("insert into alisa_organization_account(id,organization_id,username,email,activation_hash,expires_at) values(?,?,?,?,?,?)",
            activation.id(), organization, username, email, hash(activation.token()), Timestamp.from(Instant.now().plus(Duration.ofDays(2))));
        return activation;
    }

    @Transactional
    public void cancel(String organization, String actor, String account) {
        owner(organization, actor, true);
        if (jdbc.update("delete from alisa_organization_account where id=? and organization_id=? and user_id is null", account, organization) != 1)
            throw new ResponseStatusException(NOT_FOUND, "待激活账号不存在");
    }

    @Transactional
    public void manage(String organization, String actor, String account, AccountAction action) {
        owner(organization, actor, true);
        var targets = jdbc.query("""
            select user_id,email,account_status,deleted_at from alisa_organization_account
            where organization_id=? and id=? and user_id is not null for update
            """, (rs, row) -> new ManagedTarget(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4)),
            organization, account);
        if (targets.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "已激活的组织账号不存在");
        var target = targets.get(0);
        if (actor.equals(target.userId()) && action != AccountAction.RESET_PASSWORD)
            throw new ResponseStatusException(CONFLICT, "无法对自己的账号执行此操作");
        boolean allowed = switch (action) {
            case RESET_PASSWORD, SUSPEND -> "ACTIVE".equals(target.status());
            case RESUME -> "SUSPENDED".equals(target.status());
            case DELETE -> !"DELETED".equals(target.status());
            case RESTORE -> target.deletedAt() != null &&
                target.deletedAt().toInstant().plus(RESTORE_WINDOW).isAfter(Instant.now());
        };
        if (!allowed) throw new ResponseStatusException(CONFLICT, "当前账号状态不允许此操作");
        if (action == AccountAction.RESET_PASSWORD) {
            try {
                codes.send(target.email());
            } catch (MailException e) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "验证码邮件暂时发送失败，请稍后重试");
            }
            return;
        }
        String status = switch (action) {
            case SUSPEND -> "SUSPENDED";
            case DELETE -> "DELETED";
            default -> "ACTIVE";
        };
        jdbc.update("""
            update alisa_organization_account set account_status=?, deleted_at=?, session_version=session_version+1
            where id=? and organization_id=?
            """, status, action == AccountAction.DELETE ? Timestamp.from(Instant.now()) : null, account, organization);
        jdbc.update("delete from oauth2_authorization where principal_name=?", target.userId());
    }

    private record ManagedTarget(String userId, String email, String status, Timestamp deletedAt) {}

    public void sendActivationCode(String account, String token) {
        String email = pending(account, token, false).email();
        try {
            codes.sendActivation(email, account);
        } catch (MailException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "验证码邮件暂时发送失败，请稍后重试");
        }
    }

    private Account pending(String account, String token, boolean lock) {
        if (account == null || token == null || token.length() != 73) throw invalidActivation();
        var matches = jdbc.query("select id,username,email,user_id,expires_at from alisa_organization_account where id=? and activation_hash=? and user_id is null and expires_at>?" + (lock ? " for update" : ""),
            (rs, row) -> new Account(rs.getString(1), rs.getString(2), rs.getString(3), null, rs.getTimestamp(5), "PENDING", null),
            account, hash(token), Timestamp.from(Instant.now()));
        if (matches.isEmpty()) throw invalidActivation();
        return matches.get(0);
    }

    @Transactional
    public void activate(String account, String token, String password, String code) {
        if (password == null || password.isBlank() || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(BAD_REQUEST, "密码至少 12 个字符且不能超过 72 字节");
        var pending = pending(account, token, true);
        if (!codes.consumeActivation(account, code))
            throw new ResponseStatusException(BAD_REQUEST, "邮箱验证码无效或已过期");

        var user = new UserDto();
        user.setUsername(pending.username());
        user.setEmail(pending.email());
        user.setPassword(password);
        String userId = identity.register(user);
        if (userId == null) throw new ResponseStatusException(CONFLICT, "账号已存在，请联系组织管理员");
        jdbc.update("update alisa_organization_account set user_id=?,activation_hash=null,expires_at=null where id=?", userId, account);
    }

    public List<LinkedTenant> linkedTenants(String organization, String actor) {
        owner(organization, actor, false);
        return jdbc.query("""
            select t.id,t.name from alisa_organization_tenant l join alisa_tenant t on t.id=l.tenant_id
            join alisa_tenant_member m on m.tenant_id=t.id
            where l.organization_id=? and m.user_id=? and m.role='OWNER' and t.enabled=true order by t.name,t.id
            """, (rs, row) -> new LinkedTenant(rs.getString(1), rs.getString(2)), organization, actor);
    }

    @Transactional
    public void link(String organization, String actor, String tenant) {
        owner(organization, actor, true);
        tenants.lockOwnedTenant(tenant, actor);
        var linked = jdbc.queryForList("select organization_id from alisa_organization_tenant where tenant_id=?", String.class, tenant);
        if (linked.isEmpty())
            jdbc.update("insert into alisa_organization_tenant(tenant_id,organization_id) values(?,?)", tenant, organization);
        else if (!organization.equals(linked.get(0)))
            throw new ResponseStatusException(CONFLICT, "租户已关联其他组织");
    }

    @Transactional
    public void assign(String organization, String actor, String account, String tenant) {
        owner(organization, actor, true);
        if (jdbc.queryForList("select tenant_id from alisa_organization_tenant where tenant_id=? and organization_id=?",
            String.class, tenant, organization).isEmpty())
            throw new ResponseStatusException(FORBIDDEN, "租户未关联当前组织");
        var ids = jdbc.queryForList("select user_id from alisa_organization_account where id=? and organization_id=? and user_id is not null and account_status='ACTIVE'",
            String.class, account, organization);
        if (ids.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "已激活的组织账号不存在");
        tenants.assignManagedMember(tenant, actor, ids.get(0));
    }

    private static ResponseStatusException invalidActivation() {
        return new ResponseStatusException(BAD_REQUEST, "激活链接无效、已使用或已过期");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
