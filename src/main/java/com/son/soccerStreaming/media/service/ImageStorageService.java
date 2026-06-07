package com.son.soccerStreaming.media.service;

public interface ImageStorageService {

    void upload(String objectKey, byte[] content, String contentType);
}
