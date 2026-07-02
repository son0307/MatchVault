package com.son.soccerStreaming.global.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesCallerMdcAndRestoresWorkerMdc() {
        MDC.put(RequestLoggingFilter.REQUEST_ID_MDC_KEY, "request-123");
        AtomicReference<String> requestIdSeenByTask = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            requestIdSeenByTask.set(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY));
            MDC.put(RequestLoggingFilter.REQUEST_ID_MDC_KEY, "changed-by-task");
        });

        MDC.put(RequestLoggingFilter.REQUEST_ID_MDC_KEY, "worker-previous");
        decorated.run();

        assertThat(requestIdSeenByTask).hasValue("request-123");
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isEqualTo("worker-previous");
    }
}
