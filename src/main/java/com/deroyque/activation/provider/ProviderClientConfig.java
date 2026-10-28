package com.deroyque.activation.provider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configura o {@link RestClient} usado para falar com o provedor externo, com timeouts
 * explícitos de conexão e de leitura (evita threads presas se o provedor travar).
 */
@Configuration
@EnableConfigurationProperties({ProviderProperties.class, ActivationWorkerProperties.class})
public class ProviderClientConfig {

    @Bean
    ClientHttpRequestFactory providerRequestFactory(ProviderProperties properties) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return ClientHttpRequestFactories.get(settings);
    }

    @Bean
    RestClient providerRestClient(ProviderProperties properties, ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Api-Key", properties.apiKey())
                .build();
    }
}
