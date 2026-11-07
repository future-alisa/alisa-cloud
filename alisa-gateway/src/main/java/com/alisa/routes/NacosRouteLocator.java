package com.alisa.routes;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/** Compiles a complete candidate before exposing it to the gateway route cache. */
public class NacosRouteLocator implements RouteLocator, AutoCloseable, org.springframework.beans.factory.SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(NacosRouteLocator.class);
    private final ConfigService client;
    private final List<RoutePredicateFactory> predicates;
    private final List<GatewayFilterFactory> filters;
    private final GatewayProperties local;
    private final ConfigurationService configuration;
    private final ApplicationEventPublisher events;
    private final String dataId;
    private final String group;
    private volatile List<Route> routes = List.of();
    private final Listener listener = new Listener() {
        public Executor getExecutor() { return null; }
        public void receiveConfigInfo(String content) {
            try {
                replace(content);
            } catch (Exception e) {
                // Configuration may contain sensitive arguments; never print its contents.
                log.error("Rejected Nacos route update; retaining last valid routes ({})", e.getClass().getSimpleName());
            }
        }
    };

    public NacosRouteLocator(ConfigService client, List<RoutePredicateFactory> predicates,
            List<GatewayFilterFactory> filters, GatewayProperties local,
            ConfigurationService configuration, ApplicationEventPublisher events,
            String dataId, String group) {
        this.client = client;
        this.predicates = predicates;
        this.filters = filters;
        this.local = local;
        this.configuration = configuration;
        this.events = events;
        this.dataId = dataId;
        this.group = group;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            start();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize Nacos business routes", e);
        }
    }

    public synchronized void start() throws Exception {
        try {
            replace(client.getConfigAndSignListener(dataId, group, 5000, listener));
        } catch (Exception e) {
            client.removeListener(dataId, group, listener);
            throw new IllegalStateException("Cannot load initial Nacos routes: " + group + "/" + dataId, e);
        }
    }

    synchronized void replace(String content) throws Exception {
        if (!StringUtils.hasText(content)) throw new IllegalArgumentException("Missing route configuration");
        var sources = new YamlPropertySourceLoader().load("nacos-routes",
                new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        if (sources.size() != 1) throw new IllegalArgumentException("Expected one YAML document");
        var candidate = new Binder(ConfigurationPropertySources.from(sources))
                .bind("spring.cloud.gateway", Bindable.of(GatewayProperties.class))
                .orElseThrow(() -> new IllegalArgumentException("Missing gateway routes"));
        // An explicit empty list removes business routes; a missing key is rejected.
        if (sources.stream().map(source -> (org.springframework.core.env.EnumerablePropertySource<?>) source)
                .flatMap(source -> java.util.Arrays.stream(source.getPropertyNames()))
                .noneMatch(name -> name.equals("spring.cloud.gateway.routes") || name.startsWith("spring.cloud.gateway.routes[")))
            throw new IllegalArgumentException("Missing routes key");
        var ids = new HashSet<String>();
        local.getRoutes().forEach(route -> ids.add(route.getId()));
        for (var route : candidate.getRoutes()) {
            if (!StringUtils.hasText(route.getId()) || !ids.add(route.getId()))
                throw new IllegalArgumentException("Duplicate or reserved route id");
            if (route.getPredicates().isEmpty()) throw new IllegalArgumentException("Route requires predicates");
            if (route.getUri() == null || !List.of("lb", "http", "https", "ws", "wss").contains(route.getUri().getScheme()))
                throw new IllegalArgumentException("Unsupported route URI");
        }
        GatewayProperties compilation = new GatewayProperties();
        compilation.setDefaultFilters(local.getDefaultFilters());
        compilation.setFailOnRouteDefinitionError(true);
        var compiler = new RouteDefinitionRouteLocator(() -> Flux.fromIterable(candidate.getRoutes()),
                predicates, filters, compilation, configuration);
        var compiled = compiler.getRoutes().collectList().block(Duration.ofSeconds(10));
        routes = List.copyOf(compiled);
        events.publishEvent(new RefreshRoutesEvent(this));
        log.info("Loaded {} business routes from Nacos {}/{}", routes.size(), group, dataId);
    }

    public Flux<Route> getRoutes() { return Flux.defer(() -> Flux.fromIterable(routes)); }

    public void close() { client.removeListener(dataId, group, listener); }
}
