package com.son.soccerStreaming.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LiveApiClientConfig {

    @Bean
    public RestClient liveApiRestClient() {
        return RestClient.builder().build();
    }
}
