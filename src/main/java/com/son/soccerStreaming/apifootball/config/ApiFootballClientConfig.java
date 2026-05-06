package com.son.soccerStreaming.apifootball.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApiFootballClientConfig {

    @Bean
    public RestClient apiFootballRestClient() {
        return RestClient.builder().build();
    }
}
