package com.alisa.authorization.local;

import java.util.List;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.AuthorizationPolicyStore;
import org.springframework.jdbc.core.JdbcTemplate;

/** All values are bound parameters; foreign keys enforce role/application boundaries. */
public final class JdbcAuthorizationPolicyStore implements AuthorizationPolicyStore {
    private final JdbcTemplate jdbc;
    public JdbcAuthorizationPolicyStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean isGranted(AuthorizationRequest request) {
        return Boolean.TRUE.equals(jdbc.query("""
            SELECT 1 FROM alisa_authz_binding b
            JOIN alisa_authz_grant g ON b.application_id=g.application_id AND b.role_code=g.role_code
            WHERE b.application_id=? AND b.subject_type=? AND b.subject_id=?
              AND g.resource_type=? AND g.action=?
              AND (g.resource_id=? OR g.resource_id='*')
            LIMIT 1
            """, (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) result -> result.next(), request.applicationId(), request.subject().type(),
                request.subject().id(), request.resource().type(), request.action(), request.resource().id()));
    }

    @Override
    public void saveRole(PolicyRole role) {
        jdbc.update("""
            INSERT INTO alisa_authz_role(application_id, role_code, description) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE description=VALUES(description)
            """, role.applicationId(), role.code(), role.description());
    }

    @Override
    public List<PolicyRole> roles(String applicationId) {
        Fields.text(applicationId, "application id", 64);
        return jdbc.query("SELECT application_id, role_code, description FROM alisa_authz_role WHERE application_id=? ORDER BY role_code",
                (rs, row) -> new PolicyRole(rs.getString(1), rs.getString(2), rs.getString(3)), applicationId);
    }

    @Override
    public void deleteRole(String applicationId, String roleCode) {
        Fields.text(applicationId, "application id", 64);
        Fields.text(roleCode, "role code", 64);
        jdbc.update("DELETE FROM alisa_authz_role WHERE application_id=? AND role_code=?", applicationId, roleCode);
    }

    @Override
    public void grant(PermissionGrant grant) {
        jdbc.update("""
            INSERT INTO alisa_authz_grant(application_id,role_code,resource_type,resource_id,action)
            VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE action=VALUES(action)
            """, grant.applicationId(), grant.roleCode(), grant.resourceType(), grant.resourceId(), grant.action());
    }

    @Override
    public void revoke(PermissionGrant grant) {
        jdbc.update("""
            DELETE FROM alisa_authz_grant
            WHERE application_id=? AND role_code=? AND resource_type=? AND resource_id=? AND action=?
            """, grant.applicationId(), grant.roleCode(), grant.resourceType(), grant.resourceId(), grant.action());
    }

    @Override
    public void bind(RoleBinding binding) {
        jdbc.update("""
            INSERT INTO alisa_authz_binding(application_id,role_code,subject_type,subject_id)
            VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE subject_id=VALUES(subject_id)
            """, binding.applicationId(), binding.roleCode(), binding.subject().type(), binding.subject().id());
    }

    @Override
    public void unbind(RoleBinding binding) {
        jdbc.update("""
            DELETE FROM alisa_authz_binding
            WHERE application_id=? AND role_code=? AND subject_type=? AND subject_id=?
            """, binding.applicationId(), binding.roleCode(), binding.subject().type(), binding.subject().id());
    }

    @Override
    public List<PermissionGrant> grants(String applicationId, String roleCode) {
        Fields.text(applicationId, "application id", 64);
        Fields.text(roleCode, "role code", 64);
        return jdbc.query("""
            SELECT application_id,role_code,resource_type,resource_id,action FROM alisa_authz_grant
            WHERE application_id=? AND role_code=? ORDER BY resource_type,resource_id,action
            """, (rs, row) -> new PermissionGrant(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), applicationId, roleCode);
    }

    @Override
    public List<RoleBinding> bindings(String applicationId, String roleCode) {
        Fields.text(applicationId, "application id", 64);
        Fields.text(roleCode, "role code", 64);
        return jdbc.query("""
            SELECT application_id,role_code,subject_type,subject_id FROM alisa_authz_binding
            WHERE application_id=? AND role_code=? ORDER BY subject_type,subject_id
            """, (rs, row) -> new RoleBinding(rs.getString(1), rs.getString(2),
                new AuthorizationSubject(rs.getString(3), rs.getString(4))), applicationId, roleCode);
    }
}
