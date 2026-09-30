package com.son.ssegateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Map;

@SpringBootApplication
public class SseGatewayApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(SseGatewayApplication.class);
        // Bind locally until reverse-proxy/authentication deployment is configured explicitly.
        app.setDefaultProperties(Map.of("server.port", "8081", "server.address", "127.0.0.1",
                "spring.application.name", "sse-gateway", "server.forward-headers-strategy", "none"));
        app.run(args);
    }
}
