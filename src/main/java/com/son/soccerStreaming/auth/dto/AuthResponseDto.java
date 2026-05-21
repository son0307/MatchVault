package com.son.soccerStreaming.auth.dto;

import lombok.Builder;
import lombok.Getter;

public class AuthResponseDto {

    @Getter
    @Builder
    public static class Me {
        private Long id;
        private String email;
        private String nickname;
        private String role;
    }
}
