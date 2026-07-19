package com.son.soccerStreaming.apifootball.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ApiFootballClientConfig {

    @Bean
    public RestClient apiFootballRestClient(
            @Value("${live.api-football.connect-timeout:3s}") Duration connectTimeout,
            @Value("${live.api-football.request-timeout:15s}") Duration requestTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(requestTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
