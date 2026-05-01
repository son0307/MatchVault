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
                        .title("⚽ 실시간 축구 스트리밍 & 통계 API")
                        .description("대용량 트래픽 처리를 고려한 실시간 경기 스탯 누적 및 페이징 조회 API 명세서입니다.")
                        .version("v1.0.0")
                );
    }
}
