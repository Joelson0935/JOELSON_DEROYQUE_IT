package com.deroyque.activation.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do OpenAPI/Swagger.
 *
 * <p>Declara um esquema de segurança HTTP Bearer (JWT) para que o Swagger UI exiba o botão
 * "Authorize": basta colar o access token (obtido em {@code GET /dev/token} no profile local)
 * para que ele seja enviado no header {@code Authorization: Bearer ...} nas chamadas.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI activationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Activation Service API")
                        .version("v1")
                        .description("""
                                Serviço de ativação de produtos de clientes.
                                Os endpoints exigem um Bearer JWT com os escopos:
                                `activation.write` (criar) e `activation.read` (consultar/listar).
                                No profile `local`, obtenha um token em GET /dev/token e use o botão Authorize."""))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Cole o access_token retornado por GET /dev/token")));
    }
}
