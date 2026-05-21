package com.son.soccerStreaming.auth.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class AuthRequestDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Signup {
        private String email;
        private String password;
        private String nickname;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Login {
        private String email;
        private String password;
    }
}
