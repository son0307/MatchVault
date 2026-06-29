package com.son.soccerStreaming.media.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class R2StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "media.r2.enabled", havingValue = "true")
    public S3Client r2S3Client(MediaProperties properties) {
        MediaProperties.R2 r2 = properties.getR2();
        return S3Client.builder()
                .endpointOverride(URI.create(r2.getEndpoint()))
                .region(Region.of(r2.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.getAccessKey(), r2.getSecretKey())
                ))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "media.r2.enabled", havingValue = "true")
    public S3Presigner r2S3Presigner(MediaProperties properties) {
        MediaProperties.R2 r2 = properties.getR2();
        return S3Presigner.builder()
                .endpointOverride(URI.create(r2.getEndpoint()))
                .region(Region.of(r2.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.getAccessKey(), r2.getSecretKey())
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
