package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.apifootball.service.SyncProgressReporter;

public class AdminSyncJobProgressReporter implements SyncProgressReporter {

    private final Long jobId;
    private final AdminSyncJobService jobService;
    private volatile int totalUnits;
    private volatile String unitLabel;

    public AdminSyncJobProgressReporter(Long jobId, AdminSyncJobService jobService) {
        this.jobId = jobId;
        this.jobService = jobService;
    }

    @Override
    public void initialize(int totalUnits, String unitLabel) {
        this.totalUnits = Math.max(0, totalUnits);
        this.unitLabel = unitLabel;
        jobService.updateProgress(jobId, this.totalUnits, unitLabel, 0, 0, 0, 0);
    }

    @Override
    public void beginPhase(String phase, int totalUnits, String unitLabel, int savedCount) {
        this.totalUnits = Math.max(0, totalUnits);
        this.unitLabel = unitLabel;
        jobService.beginPhase(jobId, phase, this.totalUnits, unitLabel, savedCount);
    }

    @Override
    public void update(int processedUnits, int successfulUnits, int failedUnits, int savedCount) {
        jobService.updateProgress(jobId, totalUnits, unitLabel, processedUnits, successfulUnits, failedUnits, savedCount);
    }

    @Override
    public void error(String unitType, String unitId, String message) {
        jobService.addError(jobId, unitType, unitId, message);
    }

    @Override
    public void checkCancelled() {
        if (jobService.isCancellationRequested(jobId)) {
            throw new com.son.soccerStreaming.apifootball.service.SyncCancelledException();
        }
    }
}
