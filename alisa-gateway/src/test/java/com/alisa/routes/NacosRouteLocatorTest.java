package com.alisa.routes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NacosRouteLocatorTest {
    @Test void publishesOnlyValidCompleteSnapshotsAndPreservesLocalRoutes() throws Exception {
        var client = mock(ConfigService.class);
        var local = new GatewayProperties();
        var account = new org.springframework.cloud.gateway.route.RouteDefinition();
        account.setId("user-service");
        local.setRoutes(List.of(account));
        var events = new ArrayList<Object>();
        var configuration = new ConfigurationService(new DefaultListableBeanFactory(),
                () -> DefaultConversionService.getSharedInstance(), () -> null);
        var locator = new NacosRouteLocator(client,
                List.of(new PathRoutePredicateFactory(), new HeaderRoutePredicateFactory()),
                List.of(), local, configuration, events::add, "routes.yaml", "TEST");
        String valid = Files.readString(Path.of("../examples/gateway/nacos-routes.yaml"));
        when(client.getConfigAndSignListener(eq("routes.yaml"), eq("TEST"), eq(5000L), any()))
                .thenReturn(valid);
        locator.start();
        assertEquals(7, locator.getRoutes().count().block());
        var listener = org.mockito.ArgumentCaptor.forClass(Listener.class);
        verify(client).getConfigAndSignListener(eq("routes.yaml"), eq("TEST"), eq(5000L), listener.capture());
        listener.getValue().receiveConfigInfo(valid.replace("Path=/community/**", "Unknown=/community/**"));
        assertEquals(7, locator.getRoutes().count().block());
        assertEquals(1, events.size());
        assertThrows(Exception.class, () -> locator.replace(valid.replace("id: target-service", "id: user-service")));
        assertThrows(Exception.class, () -> locator.replace(null));
        assertThrows(Exception.class, () -> locator.replace("spring: [broken"));
        locator.replace(valid.replace("id: transfer-api", "id: updated-transfer-api"));
        assertTrue(locator.getRoutes().toStream().anyMatch(route -> route.getId().equals("updated-transfer-api")));
        locator.replace("spring:\n  cloud:\n    gateway:\n      routes: []\n");
        assertEquals(0, locator.getRoutes().count().block());
        assertEquals("user-service", local.getRoutes().get(0).getId());
        locator.close();
        verify(client).removeListener("routes.yaml", "TEST", listener.getValue());
    }

    @Test void gatewayContextCombinesPlatformAndNacosRoutes() throws Exception {
        var client = mock(ConfigService.class);
        when(client.getConfigAndSignListener(anyString(), anyString(), anyLong(), any()))
                .thenReturn(Files.readString(Path.of("../examples/gateway/nacos-routes.yaml")));
            new org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner()
                    .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                            org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration.class,
                            org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration.class,
                            org.springframework.cloud.gateway.config.GatewayAutoConfiguration.class))
                    .withBean("gatewayRouteConfigService", ConfigService.class, () -> client)
                    .withUserConfiguration(NacosRouteConfiguration.class)
                    .withPropertyValues("alisa.gateway.routes.nacos.enabled=true",
                            "spring.cloud.nacos.discovery.server-addr=localhost:8848",
                            "spring.cloud.gateway.routes[0].id=user-service",
                            "spring.cloud.gateway.routes[0].uri=lb://user-service",
                            "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/user/**")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        var locator = context.getBean("cachedCompositeRouteLocator", org.springframework.cloud.gateway.route.RouteLocator.class);
                        assertEquals(8, locator.getRoutes().count().block());
                    });
        verify(client).removeListener(anyString(), anyString(), any());
    }

    @Test void refusesStartupWithoutInitialConfiguration() throws Exception {
        var client = mock(ConfigService.class);
        var locator = new NacosRouteLocator(client, List.of(), List.of(), new GatewayProperties(),
                null, event -> {}, "missing", "TEST");
        assertThrows(IllegalStateException.class, locator::start);
        verify(client).removeListener(eq("missing"), eq("TEST"), any());
    }
}
