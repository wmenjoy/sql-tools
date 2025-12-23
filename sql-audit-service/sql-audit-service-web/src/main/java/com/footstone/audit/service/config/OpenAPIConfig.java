package com.footstone.audit.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {
    
    @Bean
    public OpenAPI auditServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("SQL Audit Service API")
                .version("1.0.0")
                .description("REST API for SQL audit results, statistics, and configuration management")
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}








