package com.son.soccerStreaming.news.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class NewsClientConfig {

    @Bean
    @Qualifier("serpApiRestClient")
    public RestClient serpApiRestClient(NewsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getSerpApi().getBaseUrl())
                .requestFactory(requestFactory(properties.getSerpApi().getConnectTimeout(),
                        properties.getSerpApi().getRequestTimeout()))
                .build();
    }

    @Bean
    @Qualifier("openAiNewsRestClient")
    public RestClient openAiNewsRestClient(NewsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getTranslation().getBaseUrl())
                .requestFactory(requestFactory(properties.getTranslation().getConnectTimeout(),
                        properties.getTranslation().getRequestTimeout()))
                .build();
    }

    private JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration requestTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(requestTimeout);
        return requestFactory;
    }
}
