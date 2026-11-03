package com.alisa.authorization.spi;

import java.util.List;
import com.alisa.authorization.model.*;

/** Optional capability. Read-only or external providers need not implement this port. */
public interface PolicyAdministration {
    void saveRole(PolicyRole role);
    List<PolicyRole> roles(String applicationId);
    void deleteRole(String applicationId, String roleCode);
    void grant(PermissionGrant grant);
    void revoke(PermissionGrant grant);
    void bind(RoleBinding binding);
    void unbind(RoleBinding binding);
    List<PermissionGrant> grants(String applicationId, String roleCode);
    List<RoleBinding> bindings(String applicationId, String roleCode);
}
