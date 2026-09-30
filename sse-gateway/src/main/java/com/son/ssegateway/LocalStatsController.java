package com.son.ssegateway;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Profile("local & !prod")
public class LocalStatsController {
    private final SubscriberHub hub;
    private final ConnectionAdmission admission;
    private final org.springframework.beans.factory.ObjectProvider<RedisRecovery> recovery;
    public LocalStatsController(SubscriberHub hub, ConnectionAdmission admission,
                                org.springframework.beans.factory.ObjectProvider<RedisRecovery> recovery) {
        this.hub = hub; this.admission = admission; this.recovery = recovery;
    }
    @GetMapping("/api/local/sse-gateway") public Map<String, Object> stats() {
        var result = new java.util.HashMap<String, Object>(hub.stats());
        result.put("admission", admission.stats());
        var repair = recovery.getIfAvailable();
        if (repair != null) result.put("recovery", repair.stats());
        return result;
    }
}
