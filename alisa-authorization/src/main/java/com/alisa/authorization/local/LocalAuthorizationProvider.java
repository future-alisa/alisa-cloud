package com.alisa.authorization.local;

import java.util.List;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.*;

public final class LocalAuthorizationProvider implements AuthorizationProvider, PolicyAdministration {
    private final AuthorizationPolicyStore store;
    public LocalAuthorizationProvider(AuthorizationPolicyStore store) { this.store = store; }
    @Override public String id() { return "local"; }
    @Override public AuthorizationDecision decide(AuthorizationRequest request) {
        return store.isGranted(request) ? AuthorizationDecision.permit() : AuthorizationDecision.deny("NOT_GRANTED");
    }
    @Override public void saveRole(PolicyRole role) { store.saveRole(role); }
    @Override public List<PolicyRole> roles(String app) { return store.roles(app); }
    @Override public void deleteRole(String app, String role) { store.deleteRole(app, role); }
    @Override public void grant(PermissionGrant grant) { store.grant(grant); }
    @Override public void revoke(PermissionGrant grant) { store.revoke(grant); }
    @Override public void bind(RoleBinding binding) { store.bind(binding); }
    @Override public void unbind(RoleBinding binding) { store.unbind(binding); }
    @Override public List<PermissionGrant> grants(String app, String role) { return store.grants(app, role); }
    @Override public List<RoleBinding> bindings(String app, String role) { return store.bindings(app, role); }
}
