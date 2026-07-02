package com.son.soccerStreaming.global.logging;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "http.request.id";
    static final String REQUEST_METHOD_MDC_KEY = "http.request.method";
    static final String REQUEST_URI_MDC_KEY = "url.path";

    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_URI_LENGTH = 2_048;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_REQUEST_ID_LENGTH + "}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        String method = request.getMethod();
        String uri = safeUri(request.getRequestURI());
        long startedAtNanos = System.nanoTime();
        boolean completedNormally = false;

        response.setHeader(REQUEST_ID_HEADER, requestId);
        putRequestContext(requestId, method, uri);
        log.atInfo()
                .addKeyValue("event.action", "http-request-started")
                .log("HTTP request started. method={}, uri={}", method, uri);

        try {
            filterChain.doFilter(request, response);
            completedNormally = true;
        } finally {
            if (request.isAsyncStarted()) {
                registerAsyncCompletion(request, response, requestId, method, uri, startedAtNanos);
            } else {
                int status = completedNormally || response.getStatus() >= 500
                        ? response.getStatus()
                        : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                logCompleted(method, uri, status, startedAtNanos);
            }
            clearRequestContext();
        }
    }

    private void registerAsyncCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestId,
            String method,
            String uri,
            long startedAtNanos
    ) {
        AsyncCompletionListener listener = new AsyncCompletionListener(
                requestId,
                method,
                uri,
                response,
                startedAtNanos
        );
        try {
            request.getAsyncContext().addListener(listener);
        } catch (IllegalStateException exception) {
            // The async request completed between the state check and listener registration.
            listener.complete();
        }
    }

    private String requestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    private String safeUri(String uri) {
        if (uri == null) {
            return "";
        }
        String sanitized = uri.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= MAX_URI_LENGTH ? sanitized : sanitized.substring(0, MAX_URI_LENGTH);
    }

    private void logCompleted(String method, String uri, int status, long startedAtNanos) {
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        log.atInfo()
                .addKeyValue("event.action", "http-request-completed")
                .addKeyValue("event.outcome", status < 400 ? "success" : "failure")
                .addKeyValue("event.duration", elapsedNanos)
                .addKeyValue("http.response.status_code", status)
                .log("HTTP request completed. method={}, uri={}, status={}, elapsedMs={}",
                        method, uri, status, elapsedMs);
    }

    private void putRequestContext(String requestId, String method, String uri) {
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(REQUEST_METHOD_MDC_KEY, method);
        MDC.put(REQUEST_URI_MDC_KEY, uri);
    }

    private void clearRequestContext() {
        MDC.remove(REQUEST_ID_MDC_KEY);
        MDC.remove(REQUEST_METHOD_MDC_KEY);
        MDC.remove(REQUEST_URI_MDC_KEY);
    }

    private void restoreContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }

    private final class AsyncCompletionListener implements AsyncListener {

        private final String requestId;
        private final String method;
        private final String uri;
        private final HttpServletResponse response;
        private final long startedAtNanos;
        private final AtomicBoolean completed = new AtomicBoolean();

        private AsyncCompletionListener(
                String requestId,
                String method,
                String uri,
                HttpServletResponse response,
                long startedAtNanos
        ) {
            this.requestId = requestId;
            this.method = method;
            this.uri = uri;
            this.response = response;
            this.startedAtNanos = startedAtNanos;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            complete();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // Completion is logged once when the container finishes the async request.
        }

        @Override
        public void onError(AsyncEvent event) {
            // The MVC exception path owns exception logging to avoid duplicate stack traces.
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }

        private void complete() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }

            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                putRequestContext(requestId, method, uri);
                logCompleted(method, uri, response.getStatus(), startedAtNanos);
            } finally {
                restoreContext(previousContext);
            }
        }
    }
}
