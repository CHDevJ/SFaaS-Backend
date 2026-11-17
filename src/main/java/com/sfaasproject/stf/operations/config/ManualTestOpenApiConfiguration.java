package com.sfaasproject.stf.operations.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Documents the existing authenticated B routes for the isolated local test profile. */
@Configuration(proxyBeanMethods = false)
@Profile("manual-test")
public class ManualTestOpenApiConfiguration {
    @Bean
    public OpenAPI manualTestOpenApi() {
        return new OpenAPI()
                .info(new Info().title("STF Backend B - Local Manual Test").version("5.0.0")
                        .description("Isolated sample data. Use Get-ManualTestToken.ps1 and Authorize. "
                                + "Tokens expire after 30 minutes and are held in browser memory only. "
                                + "Login is available with Get-ManualTestLogin.ps1. Real Telegram delivery is not enabled."))
                .addServersItem(new Server().url("http://localhost:8081"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
