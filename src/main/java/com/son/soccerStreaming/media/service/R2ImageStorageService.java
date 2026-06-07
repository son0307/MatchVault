package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "media.r2.enabled", havingValue = "true")
public class R2ImageStorageService implements ImageStorageService {

    private final S3Client s3Client;
    private final MediaProperties properties;

    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getR2().getBucket())
                .key(objectKey)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
    }
}
