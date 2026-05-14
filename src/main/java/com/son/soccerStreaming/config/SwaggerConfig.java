package com.son.soccerStreaming.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EPL 경기 정보 웹페이지 API")
                        .description("EPL 시즌 별 정보 모음 및 라이브 경기 상태 반영 사이트의 API 문서")
                        .version("v1.0.0")
                );
    }
}

