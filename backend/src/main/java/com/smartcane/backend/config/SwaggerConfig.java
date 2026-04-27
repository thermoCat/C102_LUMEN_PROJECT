package com.smartcane.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SmartCane 위험지도 API",
                version = "v1",
                description = "시각장애인 보행 보조 서비스 — 위험구간 수집 및 공공 API"
        )
)
public class SwaggerConfig {
}
