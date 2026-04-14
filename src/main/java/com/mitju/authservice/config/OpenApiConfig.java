package com.mitju.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration.
 * Registers bearerAuth so all secured endpoints get an "Authorize" button in Swagger UI.
 * Access at http://localhost:8081/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mitjuOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mitju Auth Service API")
                        .version("1.0.0")
                        .description("Authentication and authorisation for Mitju — Nepal's premier matrimonial platform")
                        .contact(new Contact()
                                .name("Mitju Engineering")
                                .email("eng@mitju.com")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your access token here. Get one from POST /api/auth/login")));
    }
}
