package com.son.soccerStreaming.media.service;

public class ImageDownloadException extends RuntimeException {

    public ImageDownloadException(String message) {
        super(message);
    }

    public ImageDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
