package com.alisa.user.tenant;

import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import java.security.*;
import java.nio.charset.StandardCharsets;
import com.alisa.user.mapper.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** All tenant mutations serialize on the tenant row, including ownership transfer and invitation acceptance. */
public class TenantDirectory {
    private final JdbcTemplate jdbc;
    private final UserMapper users;
    public TenantDirectory(JdbcTemplate jdbc,UserMapper users) { this.jdbc=jdbc;this.users=users; }
    public record Tenant(String id,String name,boolean enabled,String role) {}
    public record Member(String userId,String role) {}
    public record Invitation(String id,String token) {}
    public record PendingInvitation(String id,String userId,String role,java.sql.Timestamp expiresAt) {}
    public List<PendingInvitation> invitations(String tenant,String actor) {
        admin(tenant,actor);return jdbc.query("select id,user_id,role,expires_at from alisa_tenant_invitation where tenant_id=? order by expires_at",(r,n)->new PendingInvitation(r.getString(1),r.getString(2),r.getString(3),r.getTimestamp(4)),tenant);
    }
    @Transactional public void cancelInvitation(String tenant,String actor,String id) {
        lock(tenant);admin(tenant,actor);jdbc.update("delete from alisa_tenant_invitation where tenant_id=? and id=?",tenant,id);audit(tenant,actor,"INVITATION_CANCELLED",id,"{}");
    }
    public record Audit(String id,String actorId,String eventType,String objectId,String details,java.sql.Timestamp createdAt) {}
    public record Permission(String applicationId,String subjectType,String subjectId,String resourceType,String resourceId,String action) {}
    public List<Tenant> list(String user) {
        return jdbc.query("select t.id,t.name,t.enabled,m.role from alisa_tenant t join alisa_tenant_member m on m.tenant_id=t.id where m.user_id=? order by t.created_at,t.id",
            (r,n)->new Tenant(r.getString(1),r.getString(2),r.getBoolean(3),r.getString(4)),user);
    }
    public String role(String tenant,String user) {
        var values=jdbc.queryForList("select m.role from alisa_tenant_member m join alisa_tenant t on t.id=m.tenant_id where t.id=? and t.enabled=true and m.user_id=?",String.class,tenant,user);
        if(values.isEmpty()) throw denied(); return values.get(0);
    }
    public boolean active(String tenant) { return Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from alisa_tenant where id=? and enabled=true",Boolean.class,tenant)); }
    public void member(String tenant,String user) { role(tenant,user); }
    public void admin(String tenant,String user) { if(!Set.of("OWNER","ADMIN").contains(role(tenant,user))) throw denied(); }
    private void owner(String tenant,String user) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from alisa_tenant_member where tenant_id=? and user_id=? and role='OWNER'",Boolean.class,tenant,user))) throw denied();
    }
    private void lock(String tenant) {
        if(jdbc.queryForList("select id from alisa_tenant where id=? for update",String.class,tenant).isEmpty()) throw denied();
    }
    @Transactional public void lockOwnedTenant(String tenant, String actor) {
        lock(tenant);
        if (!"OWNER".equals(role(tenant, actor))) throw denied();
    }
    @Transactional public void assignManagedMember(String tenant, String actor, String user) {
        lockOwnedTenant(tenant, actor);
        if (jdbc.queryForObject("select count(*) from alisa_tenant_member where tenant_id=? and user_id=?", Integer.class, tenant, user)==0) {
            jdbc.update("insert into alisa_tenant_member(tenant_id,user_id,role,token_version) values(?,?,'MEMBER',?)",
                tenant, user, UUID.randomUUID().toString());
            audit(tenant, actor, "MANAGED_MEMBER_ASSIGNED", user, "{}");
        }
    }
    @Transactional public String create(String user,String name) {
        validateName(name); if(users.findUserByID(user)==null) throw denied();
        String id=UUID.randomUUID().toString();jdbc.update("insert into alisa_tenant(id,name,enabled,token_version) values(?,?,true,?)",id,name.trim(),UUID.randomUUID().toString());
        jdbc.update("insert into alisa_tenant_member(tenant_id,user_id,role,token_version) values(?,?,'OWNER',?)",id,user,UUID.randomUUID().toString());
        audit(id,user,"TENANT_CREATED",id,"{}");return id;
    }
    @Transactional public void update(String tenant,String actor,String name,boolean enabled) {
        lock(tenant);owner(tenant,actor);validateName(name);
        jdbc.update("update alisa_tenant set name=?,enabled=? where id=?",name.trim(),enabled,tenant);
        if(!enabled) {
            jdbc.update("update alisa_tenant set token_version=? where id=?",UUID.randomUUID().toString(),tenant);
            jdbc.update("delete from oauth2_authorization where registered_client_id in (select c.id from oauth2_registered_client c join alisa_oauth_application a on a.client_id=c.client_id where a.tenant_id=?)",tenant);
            jdbc.update("delete from alisa_tenant_invitation where tenant_id=?",tenant);
        }
        audit(tenant,actor,enabled?"TENANT_UPDATED":"TENANT_DISABLED",tenant,"{}");
    }
    public List<Member> members(String tenant,String actor) {
        member(tenant,actor);return jdbc.query("select user_id,role from alisa_tenant_member where tenant_id=? order by user_id",(r,n)->new Member(r.getString(1),r.getString(2)),tenant);
    }
    @Transactional public Invitation invite(String tenant,String actor,String user,String role) {
        lock(tenant);admin(tenant,actor);validRole(role);
        if("ADMIN".equals(role)) owner(tenant,actor);
        if (user == null || user.isBlank() || user.length() > 255) throw new IllegalArgumentException("请输入用户 ID 或邮箱");
        var account = user.contains("@") ? users.findUserByEmail(user.trim().toLowerCase(Locale.ROOT)) : users.findUserByID(user.trim());
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        user = account.getId();
        String id=UUID.randomUUID().toString(),token=UUID.randomUUID()+"."+UUID.randomUUID();
        jdbc.update("insert into alisa_tenant_invitation(id,tenant_id,user_id,role,token_hash,expires_at) values(?,?,?,?,?,?)",id,tenant,user,role,hash(token),Timestamp.from(Instant.now().plus(Duration.ofDays(2))));
        audit(tenant,actor,"MEMBER_INVITED",user,"{}");return new Invitation(id,token);
    }
    @Transactional public void accept(String tenant,String user,String id,String token) {
        lock(tenant);if(!active(tenant)) throw denied();
        var roles=jdbc.queryForList("select role from alisa_tenant_invitation where id=? and tenant_id=? and user_id=? and token_hash=? and expires_at>?",String.class,id,tenant,user,hash(token),Timestamp.from(Instant.now()));
        if(roles.size()!=1) throw denied();
        if(jdbc.queryForObject("select count(*) from alisa_tenant_member where tenant_id=? and user_id=?",Integer.class,tenant,user)==0)
            jdbc.update("insert into alisa_tenant_member(tenant_id,user_id,role,token_version) values(?,?,?,?)",tenant,user,roles.get(0),UUID.randomUUID().toString());
        jdbc.update("delete from alisa_tenant_invitation where id=?",id);audit(tenant,user,"INVITATION_ACCEPTED",user,"{}");
    }
    @Transactional public void changeRole(String tenant,String actor,String user,String value) {
        lock(tenant);admin(tenant,actor);validRole(value);String existing=role(tenant,user);
        if("OWNER".equals(existing)) throw new ResponseStatusException(HttpStatus.CONFLICT,"请使用所有权转移");
        if("ADMIN".equals(existing) || "ADMIN".equals(value)) owner(tenant,actor);
        jdbc.update("update alisa_tenant_member set role=? where tenant_id=? and user_id=?",value,tenant,user);
        audit(tenant,actor,"MEMBER_ROLE_CHANGED",user,"{\"role\":\""+value+"\"}");
    }
    @Transactional public void remove(String tenant,String actor,String user) {
        lock(tenant);String existing=role(tenant,user);
        if("OWNER".equals(existing)) throw new ResponseStatusException(HttpStatus.CONFLICT,"所有者不能直接退出");
        if(!actor.equals(user)) {admin(tenant,actor);if("ADMIN".equals(existing)) owner(tenant,actor);}
        jdbc.update("delete from alisa_tenant_member where tenant_id=? and user_id=?",tenant,user);
        jdbc.update("delete from alisa_tenant_invitation where tenant_id=? and user_id=?",tenant,user);
        jdbc.update("delete from alisa_tenant_permission where tenant_id=? and subject_type='USER' and subject_id=?",tenant,user);
        // Outstanding authorization codes cannot be exchanged after membership removal (client repository + token validator).
        jdbc.update("delete from oauth2_authorization where principal_name=? and registered_client_id in (select c.id from oauth2_registered_client c join alisa_oauth_application a on a.client_id=c.client_id where a.tenant_id=?)",user,tenant);
        jdbc.update("delete from oauth2_authorization_consent where principal_name=? and registered_client_id in (select c.id from oauth2_registered_client c join alisa_oauth_application a on a.client_id=c.client_id where a.tenant_id=?)",user,tenant);
        audit(tenant,actor,"MEMBER_REMOVED",user,"{}");
    }
    @Transactional public void transfer(String tenant,String actor,String next) {
        lock(tenant);owner(tenant,actor);member(tenant,next);
        if(actor.equals(next)) return;
        jdbc.update("update alisa_tenant_member set role='ADMIN' where tenant_id=? and user_id=?",tenant,actor);
        jdbc.update("update alisa_tenant_member set role='OWNER' where tenant_id=? and user_id=?",tenant,next);
        audit(tenant,actor,"OWNERSHIP_TRANSFERRED",next,"{}");
    }
    public List<Permission> permissions(String tenant,String actor) {
        admin(tenant,actor);return jdbc.query("select application_id,subject_type,subject_id,resource_type,resource_id,action from alisa_tenant_permission where tenant_id=?",(r,n)->new Permission(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),tenant);
    }
    @Transactional public void permission(String tenant,String actor,Permission p,boolean grant) {
        lock(tenant);admin(tenant,actor);
        if(p.subjectType()==null || !Set.of("USER","APPLICATION").contains(p.subjectType()) || p.subjectId()==null || p.subjectId().isBlank() || p.resourceId()==null || p.resourceId().isBlank() || p.resourceId().length()>191) throw new IllegalArgumentException("Invalid permission");
        if("USER".equals(p.subjectType())) member(tenant,p.subjectId());
        else if(jdbc.queryForObject("select count(*) from alisa_oauth_application where tenant_id=? and client_id=? and kind='machine' and enabled=true",Integer.class,tenant,p.subjectId())!=1) throw denied();
        Object[] args={tenant,p.applicationId(),p.subjectType(),p.subjectId(),p.resourceType(),p.resourceId(),p.action()};
        jdbc.update("delete from alisa_tenant_permission where tenant_id=? and application_id=? and subject_type=? and subject_id=? and resource_type=? and resource_id=? and action=?",args);
        if(grant) jdbc.update("insert into alisa_tenant_permission(tenant_id,application_id,subject_type,subject_id,resource_type,resource_id,action) values(?,?,?,?,?,?,?)",args);
        audit(tenant,actor,grant?"PERMISSION_GRANTED":"PERMISSION_REVOKED",p.subjectId(),"{}");
    }
    public boolean allowed(String tenant,String app,String type,String subject,String resourceType,String resourceId,String action) {
        if(tenant==null || tenant.isBlank() || !active(tenant)) return false;
        if("USER".equals(type)) {try {String role=role(tenant,subject);if(Set.of("OWNER","ADMIN").contains(role)) return true;if("READER".equals(role) && !"read".equals(action)) return false;} catch(ResponseStatusException e) {return false;}}
        else if(!"APPLICATION".equals(type)) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from alisa_tenant_permission where tenant_id=? and application_id=? and subject_type=? and subject_id=? and resource_type=? and (resource_id=? or resource_id='*') and action=?",Boolean.class,tenant,app,type,subject,resourceType,resourceId,action));
    }
    public String tokenVersion(String tenant,String user) {
        member(tenant,user);return jdbc.queryForObject("select concat(t.token_version,':',m.token_version) from alisa_tenant t join alisa_tenant_member m on m.tenant_id=t.id where t.id=? and m.user_id=?",String.class,tenant,user);
    }
    public void validateTokenVersion(String tenant,String user,String version) {
        if(!tokenVersion(tenant,user).equals(version)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"租户会话已失效，请重新选择空间");
    }
    public List<Audit> audits(String tenant,String actor,int limit,String before) {
        admin(tenant,actor);if(limit<1 || limit>200) throw new IllegalArgumentException("Limit 1..200");
        // Stable cursor orders by UUID to avoid skipping records sharing a timestamp.
        return jdbc.query("select id,actor_id,event_type,object_id,details,created_at from alisa_tenant_audit where tenant_id=? and (?='' or id<?) order by id desc limit ?",
            (r,n)->new Audit(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6)),tenant,before,before,limit);
    }
    public void audit(String tenant,String actor,String event,String object,String details) {
        if(tenant==null || tenant.isBlank()) return;
        jdbc.update("insert into alisa_tenant_audit(id,tenant_id,actor_id,event_type,object_id,details) values(?,?,?,?,?,?)",String.format("%019d-%s",System.currentTimeMillis(),UUID.randomUUID()),tenant,actor,event,object,details);
    }
    private void validRole(String value) {if(value==null || !Set.of("ADMIN","MEMBER","READER").contains(value)) throw new IllegalArgumentException("Invalid member role");}
    private void validateName(String value) {if(value==null || value.isBlank() || value.length()>100) throw new IllegalArgumentException("Invalid tenant name");}
    private String hash(String value) {if(value==null || value.isBlank() || value.length()>200) throw new IllegalArgumentException("Invalid invitation token");try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));} catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private ResponseStatusException denied() {return new ResponseStatusException(HttpStatus.FORBIDDEN,"租户权限不足");}
}
