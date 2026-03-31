package com.example.currencyexchange.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration.
 * UI available at /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    public OpenAPI currencyExchangeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Currency Exchange Rates API")
                        .description(
                                "Provides real-time and historical currency exchange rates fetched from multiple providers."
                        )
                        .version("1.0.0")
                        .contact(
                                new Contact()
                                        .name("Currency Exchange Service")
                                        .email("support@currency-exchange.example.com")
                        ))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH, new SecurityScheme()
                                .name(BASIC_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")));
    }
}
