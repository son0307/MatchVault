package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class ImageDownloader {

    private static final Pattern API_FOOTBALL_IMAGE_PATH = Pattern.compile(
            "^/football/(players|teams|venues)/\\d+\\.(png|jpg|jpeg|webp)$",
            Pattern.CASE_INSENSITIVE
    );

    private final MediaProperties properties;
    private final HttpClient httpClient;

    public ImageDownloader(MediaProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getCache().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ImageDownloadResult download(String sourceUrl) {
        URI uri = parseAndValidateUri(sourceUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getCache().getRequestTimeout())
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new ImageDownloadException("Image download failed with status " + response.statusCode());
            }

            try (InputStream body = response.body()) {
                String contentType = normalizeContentType(response.headers().firstValue("content-type"));
                String extension = extensionOf(contentType)
                        .orElseThrow(() -> new ImageDownloadException("Unsupported image content type"));

                byte[] content = readLimited(body, properties.getCache().getMaxBytes());
                return new ImageDownloadResult(content, contentType, extension);
            }
        } catch (IOException e) {
            throw new ImageDownloadException("Image download failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageDownloadException("Image download interrupted", e);
        }
    }

    private URI parseAndValidateUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ImageDownloadException("Image URL is blank");
        }

        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException e) {
            throw new ImageDownloadException("Invalid image URL", e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ImageDownloadException("Only HTTPS image URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || properties.getCache().getAllowedHosts().stream().noneMatch(host::equalsIgnoreCase)) {
            throw new ImageDownloadException("Image host is not allowed");
        }
        if ("media.api-sports.io".equalsIgnoreCase(host)
                && !API_FOOTBALL_IMAGE_PATH.matcher(uri.getPath()).matches()) {
            throw new ImageDownloadException("API-Football image path is not allowed");
        }
        return uri;
    }

    private String normalizeContentType(Optional<String> value) {
        return value.orElse("")
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private Optional<String> extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> Optional.of("png");
            case "image/jpeg" -> Optional.of("jpg");
            case "image/webp" -> Optional.of("webp");
            default -> Optional.empty();
        };
    }

    private byte[] readLimited(InputStream inputStream, long maxBytes) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new ImageDownloadException("Image exceeds maximum allowed size");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
