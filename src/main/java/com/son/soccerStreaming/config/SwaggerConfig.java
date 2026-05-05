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
                        .title("???ㅼ떆媛?異뺢뎄 ?ㅽ듃由щ컢 & ?듦퀎 API")
                        .description("??⑸웾 ?몃옒??泥섎━瑜?怨좊젮???ㅼ떆媛?寃쎄린 ?ㅽ꺈 ?꾩쟻 諛??섏씠吏?議고쉶 API 紐낆꽭?쒖엯?덈떎.")
                        .version("v1.0.0")
                );
    }
}

