package com.deroyque.activation.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "provider")
public record ProviderProperties(
        String baseUrl,
        String activatePath,
        Duration connectTimeout,
        Duration readTimeout,
        String apiKey
) {
}
