package com.alisa.routes;

import java.util.Properties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "alisa.gateway.routes.nacos.enabled", havingValue = "true")
public class NacosRouteConfiguration {
    @Bean(destroyMethod = "shutDown")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name = "gatewayRouteConfigService")
    ConfigService gatewayRouteConfigService(
            @Value("${spring.cloud.nacos.discovery.server-addr}") String address,
            @Value("${spring.cloud.nacos.discovery.namespace:}") String namespace,
            @Value("${spring.cloud.nacos.discovery.username:}") String username,
            @Value("${spring.cloud.nacos.discovery.password:}") String password) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", address);
        properties.setProperty("namespace", namespace);
        properties.setProperty("username", username);
        properties.setProperty("password", password);
        return NacosFactory.createConfigService(properties);
    }

    @Bean(destroyMethod = "close")
    NacosRouteLocator nacosRouteLocator(ConfigService gatewayRouteConfigService,
            List<RoutePredicateFactory> predicates, List<GatewayFilterFactory> filters,
            GatewayProperties gatewayProperties, ConfigurationService configurationService,
            ApplicationEventPublisher events,
            @Value("${alisa.gateway.routes.nacos.data-id:alisa-gateway-routes.yaml}") String dataId,
            @Value("${alisa.gateway.routes.nacos.group:ALISA_GATEWAY}") String group) {
        return new NacosRouteLocator(gatewayRouteConfigService, predicates, filters,
                gatewayProperties, configurationService, events, dataId, group);
    }
}
