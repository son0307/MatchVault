package com.son.soccerStreaming.news.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NewsClientConfig {

    @Bean
    @Qualifier("serpApiRestClient")
    public RestClient serpApiRestClient(NewsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getSerpApi().getBaseUrl())
                .build();
    }

    @Bean
    @Qualifier("openAiNewsRestClient")
    public RestClient openAiNewsRestClient(NewsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getTranslation().getBaseUrl())
                .build();
    }
}
