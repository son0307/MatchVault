package com.son.soccerStreaming.admin.entity;

public enum AdminMediaTargetType {
    PLAYER_PHOTO("admin/player-photos"),
    TEAM_LOGO("admin/team-logos"),
    VENUE_IMAGE("admin/venue-images");

    private final String objectKeyPrefix;

    AdminMediaTargetType(String objectKeyPrefix) {
        this.objectKeyPrefix = objectKeyPrefix;
    }

    public String objectKeyPrefix(Long targetId) {
        return objectKeyPrefix + "/" + targetId + "/";
    }
}
