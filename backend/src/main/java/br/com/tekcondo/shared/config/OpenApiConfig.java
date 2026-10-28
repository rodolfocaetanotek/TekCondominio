package br.com.tekcondo.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tekcondoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tek Condomínio — API de Gestão de Condomínios")
                        .version("v1")
                        .description("API REST da plataforma Tek Condomínio. Autentique em POST /api/v1/auth/login e use o "
                                + "accessToken retornado no botão Authorize. Erros seguem a RFC 9457 (problem+json)."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
