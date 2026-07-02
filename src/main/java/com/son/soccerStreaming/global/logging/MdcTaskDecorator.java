package com.son.soccerStreaming.global.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            try {
                restoreContext(callerContext);
                runnable.run();
            } finally {
                restoreContext(workerContext);
            }
        };
    }

    private void restoreContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
