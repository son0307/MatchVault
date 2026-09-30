package com.son.soccerStreaming.live.controller;

import com.son.soccerStreaming.live.service.SseRecoverySnapshotService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "live.sse.transport", havingValue = "redis")
public class SseRecoveryController {
    private final SseRecoverySnapshotService snapshots;
    public SseRecoveryController(SseRecoverySnapshotService snapshots) {
        this.snapshots = snapshots;
    }
    @GetMapping("/api/v1/live/recovery/fixtures/{fixtureId}")
    public ResponseEntity<SseRecoverySnapshotService.Bundle> read(@PathVariable long fixtureId) {
        if (fixtureId <= 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        var bundle = snapshots.read(fixtureId);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(bundle);
    }
}
