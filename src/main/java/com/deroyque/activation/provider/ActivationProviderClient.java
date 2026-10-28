package com.deroyque.activation.provider;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ActivationProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ActivationProviderClient.class);

    private final RestClient restClient;
    private final ProviderProperties properties;

    public ActivationProviderClient(RestClient providerRestClient, ProviderProperties properties) {
        this.restClient = providerRestClient;
        this.properties = properties;
    }

    public ProviderOutcome activate(ActivateCommand command) {
        try {
            return restClient.post()
                    .uri(properties.activatePath())
                    
                    .header("Idempotency-Key", command.idempotencyKey().toString())
                    .body(Map.of(
                            "externalId", command.externalId(),
                            "customerId", command.customerId(),
                            "productCode", command.productCode()
                    ))
                    .exchange((request, response) -> classify(response));
        } catch (ResourceAccessException e) {
            
            log.warn("Falha de acesso ao provedor (timeout/conexão): {}", e.getMessage());
            return ProviderOutcome.transient_("Timeout ou falha de conexão com o provedor");
        } catch (Exception e) {
            log.warn("Erro inesperado ao chamar o provedor: {}", e.getMessage());
            return ProviderOutcome.transient_("Erro inesperado na chamada ao provedor");
        }
    }

    private ProviderOutcome classify(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        HttpStatusCode status = response.getStatusCode();
        int code = status.value();
        if (status.is2xxSuccessful()) {
            return ProviderOutcome.success();
        }
        if (code == 429 || code == 503) {
            Integer retryAfter = parseRetryAfter(response);
            return ProviderOutcome.transient_("Provedor sinalizou indisponibilidade (HTTP " + code + ")", retryAfter);
        }
        if (status.is5xxServerError()) {
            return ProviderOutcome.transient_("Erro do provedor (HTTP " + code + ")");
        }
        
        return ProviderOutcome.permanent("Requisição rejeitada pelo provedor (HTTP " + code + ")");
    }

    private Integer parseRetryAfter(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            String header = response.getHeaders().getFirst("Retry-After");
            if (header == null || header.isBlank()) {
                return null;
            }
            
            return Integer.parseInt(header.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
