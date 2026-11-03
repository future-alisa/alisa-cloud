package com.alisa.authorization.local;

import java.util.Map;
import java.util.UUID;
import com.alisa.authorization.model.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class JdbcAuthorizationPolicyStoreTest {
    SingleConnectionDataSource dataSource;
    JdbcAuthorizationPolicyStore store;
    @BeforeEach void setup() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", true);
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("db/authorization-schema.sql"));
        store = new JdbcAuthorizationPolicyStore(new JdbcTemplate(dataSource));
        store.saveRole(new PolicyRole("app-a", "editor", "Editor"));
        store.saveRole(new PolicyRole("app-b", "editor", "Unrelated editor"));
        store.bind(new RoleBinding("app-a", "editor", AuthorizationSubject.user("Alice")));
    }
    @AfterEach void close() { dataSource.destroy(); }
    AuthorizationRequest request(String app, String user, String resource, String action) {
        return new AuthorizationRequest(app, AuthorizationSubject.user(user),
                new AuthorizationResource("document", resource), action, Map.of());
    }
    @Test void grantsAreScopedByApplicationSubjectResourceAndAction() {
        store.grant(new PermissionGrant("app-a", "editor", "document", "42", "read"));
        assertTrue(store.isGranted(request("app-a", "Alice", "42", "read")));
        assertFalse(store.isGranted(request("app-b", "Alice", "42", "read")));
        assertFalse(store.isGranted(request("app-a", "alice", "42", "read")));
        assertFalse(store.isGranted(request("app-a", "Alice", "43", "read")));
        assertFalse(store.isGranted(request("app-a", "Alice", "42", "update")));
    }
    @Test void wildcardResourceGrantsRemainScopedAndRevocationTakesEffectImmediately() {
        var grant = new PermissionGrant("app-a", "editor", "document", "*", "read");
        store.grant(grant); store.grant(grant);
        assertTrue(store.isGranted(request("app-a", "Alice", "anything", "read")));
        store.revoke(grant);
        assertFalse(store.isGranted(request("app-a", "Alice", "anything", "read")));
    }
    @Test void roleDeletionAtomicallyRemovesBindingsAndGrants() {
        store.grant(new PermissionGrant("app-a", "editor", "document", "42", "read"));
        store.deleteRole("app-a", "editor");
        assertTrue(store.bindings("app-a", "editor").isEmpty());
        assertTrue(store.grants("app-a", "editor").isEmpty());
        assertFalse(store.isGranted(request("app-a", "Alice", "42", "read")));
        assertEquals(1, store.roles("app-b").size());
    }
    @Test void bindingsAreIdempotentAndCanBeRemoved() {
        var binding = new RoleBinding("app-a", "editor", AuthorizationSubject.user("Alice"));
        store.bind(binding);
        assertEquals(1, store.bindings("app-a", "editor").size());
        store.grant(new PermissionGrant("app-a", "editor", "document", "42", "read"));
        store.unbind(binding);
        assertFalse(store.isGranted(request("app-a", "Alice", "42", "read")));
    }
    @Test void missingRolesCannotReceiveBindingsOrGrants() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> store.bind(new RoleBinding("app-a", "missing", AuthorizationSubject.user("Alice"))));
    }
}
