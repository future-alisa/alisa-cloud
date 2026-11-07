package com.alisa.routes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.util.StringUtils;

/** One-time deployment bootstrap; existing Nacos content remains operator-owned. */
public final class NacosRoutePublisher {
    private NacosRoutePublisher() {}

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", required("NACOS_SERVER_ADDR"));
        properties.setProperty("namespace", env("NACOS_NAMESPACE", ""));
        properties.setProperty("username", env("NACOS_USERNAME", ""));
        properties.setProperty("password", env("NACOS_PASSWORD", ""));
        String dataId = env("GATEWAY_NACOS_ROUTES_DATA_ID", "alisa-gateway-routes.yaml");
        String group = env("GATEWAY_NACOS_ROUTES_GROUP", "ALISA_GATEWAY");
        ConfigService client = NacosFactory.createConfigService(properties);
        try {
            String current = client.getConfig(dataId, group, 10000);
            if (StringUtils.hasText(current)) {
                System.out.println("Existing Nacos routes retained: " + group + "/" + dataId);
                return;
            }
            String content = Files.readString(Path.of(args[0]));
            if (!client.publishConfig(dataId, group, content, "yaml"))
                throw new IllegalStateException("Nacos did not accept initial route configuration");
            if (!content.equals(client.getConfig(dataId, group, 10000)))
                throw new IllegalStateException("Nacos route read-back did not match");
            System.out.println("Published initial Nacos routes: " + group + "/" + dataId);
        } finally {
            client.shutDown();
        }
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}
