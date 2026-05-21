package com.son.soccerStreaming.dev;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/data/bulk")
@RequiredArgsConstructor
public class BulkDataController {

    private final BulkDataService bulkDataService;

    @PostMapping("/player-stats")
    public BulkDataService.BulkInsertResult addBulkPlayerStats(
            @RequestParam(defaultValue = "100000") int targetStatCount
    ) {
        return bulkDataService.bulkInsertPlayerStats(targetStatCount);
    }
}

