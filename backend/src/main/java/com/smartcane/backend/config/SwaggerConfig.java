package com.smartcane.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI smartCaneOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartCane 위험지도 API")
                        .version("v1")
                        .description("시각장애인 보행 보조 서비스 — 위험구간 수집 및 공공 API"))
                .servers(List.of(
                        new Server().url("/api").description("프록시 경유 (운영)"),
                        new Server().url("/").description("로컬 직접 접근")
                ));
    }
}
