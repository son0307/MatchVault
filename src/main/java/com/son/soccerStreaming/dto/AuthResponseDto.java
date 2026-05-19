package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;

public class AuthResponseDto {

    @Getter
    @Builder
    public static class Me {
        private Long id;
        private String email;
        private String nickname;
    }
}
