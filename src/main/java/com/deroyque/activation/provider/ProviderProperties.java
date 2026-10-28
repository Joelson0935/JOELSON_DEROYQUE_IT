package com.deroyque.activation.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração do provedor externo de ativação (prefixo {@code provider} no application.yml).
 */
@ConfigurationProperties(prefix = "provider")
public record ProviderProperties(
        String baseUrl,
        String activatePath,
        Duration connectTimeout,
        Duration readTimeout,
        String apiKey
) {
}
