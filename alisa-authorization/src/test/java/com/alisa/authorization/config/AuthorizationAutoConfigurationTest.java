package com.alisa.authorization.config;

import com.alisa.authorization.AuthorizationEngine;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthorizationAutoConfigurationTest {
    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthorizationAutoConfiguration.class));
    @Test void theLibraryDoesNotStartInfrastructureUnlessEnabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AuthorizationEngine.class));
    }
    @Test void customStoresAndLocalProviderAreAutomaticallyWired() {
        var store = mock(AuthorizationPolicyStore.class);
        when(store.isGranted(any())).thenReturn(true);
        runner.withPropertyValues("alisa.authorization.enabled=true", "alisa.authorization.applications.app=local")
                .withBean(AuthorizationPolicyStore.class, () -> store).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AuthorizationEngine.class).evaluate(new AuthorizationRequest("app",
                            AuthorizationSubject.user("u"), new AuthorizationResource("file", "1"), "read", Map.of())).allowed()).isTrue();
                });
    }
    @Test void externalProvidersCanRunWithoutJdbcOrALocalPolicyStore() {
        AuthorizationProvider remote = new AuthorizationProvider() {
            public String id() { return "company"; }
            public AuthorizationDecision decide(AuthorizationRequest request) { return AuthorizationDecision.permit(); }
        };
        runner.withPropertyValues("alisa.authorization.enabled=true", "alisa.authorization.applications.app=company")
                .withBean(AuthorizationProvider.class, () -> remote).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuthorizationEngine.class);
                    assertThat(context).doesNotHaveBean(AuthorizationPolicyStore.class);
                });
    }
    @Test void jdbcStoresAndLocalProvidersAreWiredInTheActualAutoConfigurationOrder() {
        runner.withPropertyValues("alisa.authorization.enabled=true", "alisa.authorization.applications.app=local")
                .withBean(org.springframework.jdbc.core.JdbcTemplate.class, () -> mock(org.springframework.jdbc.core.JdbcTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuthorizationPolicyStore.class);
                    assertThat(context).hasSingleBean(AuthorizationEngine.class);
                });
    }
}
