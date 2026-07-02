package com.son.soccerStreaming.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private Logger filterLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        logAppender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void cleanUp() {
        filterLogger.detachAppender(logAppender);
        logAppender.stop();
        MDC.clear();
    }

    @Test
    void reusesSafeRequestIdAndClearsMdcAfterCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fixtures/100");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "client-request_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeenByApplication = new AtomicReference<>();
        AtomicReference<String> methodSeenByApplication = new AtomicReference<>();
        AtomicReference<String> uriSeenByApplication = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            requestIdSeenByApplication.set(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY));
            methodSeenByApplication.set(MDC.get(RequestLoggingFilter.REQUEST_METHOD_MDC_KEY));
            uriSeenByApplication.set(MDC.get(RequestLoggingFilter.REQUEST_URI_MDC_KEY));
            ((MockHttpServletResponse) servletResponse).setStatus(204);
        };

        filter.doFilter(request, response, chain);

        assertThat(requestIdSeenByApplication).hasValue("client-request_123");
        assertThat(methodSeenByApplication).hasValue("GET");
        assertThat(uriSeenByApplication).hasValue("/api/v1/fixtures/100");
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("client-request_123");
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_METHOD_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_URI_MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "unsafe request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String generatedRequestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(generatedRequestId).isNotEqualTo("unsafe request id");
        assertThat(UUID.fromString(generatedRequestId)).isNotNull();
    }

    @Test
    void keepsAsyncRequestOpenAndClearsRequestThreadMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/live/stream/fixtures/100");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeenByApplication = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            requestIdSeenByApplication.set(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY));
            ((HttpServletRequest) servletRequest).startAsync();
        });

        assertThat(request.isAsyncStarted()).isTrue();
        assertThat(requestIdSeenByApplication.get()).isNotBlank();
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();

        request.getAsyncContext().complete();

        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void keepsLifecycleLogsAtInfoForServerErrorResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(500));

        assertThat(logAppender.list).hasSize(2);
        assertThat(logAppender.list).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
        ILoggingEvent completed = logAppender.list.get(1);
        assertThat(completed.getFormattedMessage())
                .contains("HTTP request completed", "status=500");
        assertThat(keyValue(completed, "event.action")).isEqualTo("http-request-completed");
        assertThat(keyValue(completed, "event.outcome")).isEqualTo("failure");
        assertThat(keyValue(completed, "http.response.status_code")).isEqualTo(500);
        assertThat(keyValue(completed, "event.duration")).isInstanceOf(Long.class);
        assertThat(completed.getMDCPropertyMap())
                .containsEntry(RequestLoggingFilter.REQUEST_METHOD_MDC_KEY, "GET")
                .containsEntry(RequestLoggingFilter.REQUEST_URI_MDC_KEY, "/api/v1/test");
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }
}
