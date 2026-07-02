package com.son.soccerStreaming.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggingTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void prodProfileEnablesEcsConsoleAndRollingFileLogging() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> prod = loader.load("prod", new ClassPathResource("application-prod.yml"));
        List<PropertySource<?>> local = loader.load("local", new ClassPathResource("application-local.yml"));

        assertThat(property(prod, "logging.structured.format.console")).isEqualTo("ecs");
        assertThat(property(prod, "logging.structured.format.file")).isEqualTo("ecs");
        assertThat(property(prod, "logging.structured.ecs.service.environment")).isEqualTo("production");
        assertThat(property(prod, "logging.file.name"))
                .isEqualTo("${LOG_FILE_NAME:logs/application.json}");
        assertThat(property(prod, "logging.logback.rollingpolicy.file-name-pattern"))
                .isEqualTo("${LOG_ARCHIVE_PATTERN:logs/application.%d{yyyy-MM-dd}.%i.json.gz}");
        assertThat(property(prod, "logging.logback.rollingpolicy.max-file-size"))
                .isEqualTo("${LOG_MAX_FILE_SIZE:100MB}");
        assertThat(property(prod, "logging.logback.rollingpolicy.max-history"))
                .isEqualTo("${LOG_MAX_HISTORY:30}");
        assertThat(property(prod, "logging.logback.rollingpolicy.total-size-cap"))
                .isEqualTo("${LOG_TOTAL_SIZE_CAP:5GB}");
        assertThat(property(local, "logging.structured.format.console")).isNull();
        assertThat(property(local, "logging.structured.format.file")).isNull();
        assertThat(property(local, "logging.file.name")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ecsEncoderCreatesNestedHttpFieldsAndSingleLineStackTrace() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            MDC.put(RequestLoggingFilter.REQUEST_ID_MDC_KEY, "request-123");
            MDC.put(RequestLoggingFilter.REQUEST_METHOD_MDC_KEY, "POST");
            MDC.put(RequestLoggingFilter.REQUEST_URI_MDC_KEY, "/api/v1/admin/media/uploads/complete");
            new GlobalExceptionHandler().handleCustomException(new CustomException(
                    ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE,
                    new IllegalStateException("storage unavailable")
            ));

            String json = encodeAsEcs(appender.list.get(appender.list.size() - 1));
            Map<String, Object> root = new ObjectMapper().readValue(json, Map.class);

            assertThat(json.strip().lines()).hasSize(1);
            assertThat(child(root, "service"))
                    .containsEntry("name", "soccerStreaming")
                    .containsEntry("environment", "production");
            assertThat(child(root, "log")).containsEntry("level", "ERROR");
            assertThat(child(child(root, "http"), "request"))
                    .containsEntry("id", "request-123")
                    .containsEntry("method", "POST");
            assertThat(child(child(root, "http"), "response"))
                    .containsEntry("status_code", 503);
            assertThat(child(root, "url"))
                    .containsEntry("path", "/api/v1/admin/media/uploads/complete");
            assertThat(child(root, "event"))
                    .containsEntry("action", "http-request-failed")
                    .containsEntry("outcome", "failure")
                    .containsEntry("code", "ADMIN_MEDIA_STORAGE_UNAVAILABLE");
            assertThat(child(root, "error"))
                    .containsEntry("type", CustomException.class.getName())
                    .containsEntry("message", ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE.getMessage())
                    .containsKey("stack_trace");
            assertThat(child(root, "error").get("stack_trace").toString())
                    .contains(IllegalStateException.class.getName(), "storage unavailable");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private String encodeAsEcs(ILoggingEvent event) {
        LoggerContext context = new LoggerContext();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "soccerStreaming")
                .withProperty("logging.structured.ecs.service.environment", "production");
        context.putObject(Environment.class.getName(), environment);

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.start();
        try {
            return new String(encoder.encode(event), StandardCharsets.UTF_8);
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> child(Map<String, Object> parent, String name) {
        return (Map<String, Object>) parent.get(name);
    }
}
