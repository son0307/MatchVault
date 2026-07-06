package com.son.soccerStreaming.apifootball.service;

public interface SyncProgressReporter {

    SyncProgressReporter NO_OP = new SyncProgressReporter() {
    };

    default void initialize(int totalUnits, String unitLabel) {
    }

    default void beginPhase(String phase, int totalUnits, String unitLabel, int savedCount) {
        initialize(totalUnits, unitLabel);
    }

    default void update(int processedUnits, int successfulUnits, int failedUnits, int savedCount) {
    }

    default void error(String unitType, String unitId, String message) {
    }

    default void checkCancelled() {
    }
}
