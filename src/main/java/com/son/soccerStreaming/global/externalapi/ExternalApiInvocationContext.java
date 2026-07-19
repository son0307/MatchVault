package com.son.soccerStreaming.global.externalapi;

public record ExternalApiInvocationContext(Long adminUserId, Long teamId, Long articleId, Integer batchSize) {
    private static final ExternalApiInvocationContext SYSTEM = new ExternalApiInvocationContext(null, null, null, null);

    public static ExternalApiInvocationContext system() { return SYSTEM; }

    public static ExternalApiInvocationContext admin(Long adminUserId, Long teamId, Long articleId, Integer batchSize) {
        return new ExternalApiInvocationContext(adminUserId, teamId, articleId, batchSize);
    }

    public boolean isAdminRequest() { return adminUserId != null; }
}
