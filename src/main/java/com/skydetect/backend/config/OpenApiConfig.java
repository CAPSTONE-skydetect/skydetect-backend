package com.skydetect.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI skydetectOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SkyDetect Backend API")
                        .version("v0.0.1")
                        .description("""
                                관제 화면용 백엔드 API.

                                인증은 세션 쿠키(JSESSIONID) 방식이다. 로그인하면 브라우저가 쿠키를
                                자동으로 들고 다니므로 Swagger UI 에서 별도 인증 설정이 필요 없다.
                                """))
                // 실제 인증에 필요한 설정은 아니다. 인증 방식을 문서에 드러내기 위한 선언이다.
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }
}
