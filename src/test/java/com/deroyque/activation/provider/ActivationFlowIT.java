package com.deroyque.activation.provider;

import com.deroyque.activation.AbstractPostgresIT;
import com.deroyque.activation.api.dto.CreateActivationRequest;
import com.deroyque.activation.domain.ActivationRequest;
import com.deroyque.activation.domain.ActivationRequestRepository;
import com.deroyque.activation.domain.RequestStatus;
import com.deroyque.activation.service.ActivationService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Teste de integração do fluxo assíncrono fim-a-fim: a API cria a solicitação (PENDING), o worker
 * a processa chamando o provedor (WireMock) e o status final é atingido. Usa Awaitility para
 * aguardar o processamento em background.
 */
class ActivationFlowIT extends AbstractPostgresIT {

    private static WireMockServer wireMock;

    @Autowired
    private ActivationService service;

    @Autowired
    private ActivationRequestRepository repository;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(8089));
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void providerProps(DynamicPropertyRegistry registry) {
        registry.add("provider.base-url", () -> "http://localhost:8089");
        // Liga o worker com poll rápido para o teste observar o processamento.
        registry.add("activation.worker.enabled", () -> "true");
        registry.add("activation.worker.poll-interval", () -> "300");
        registry.add("activation.worker.max-attempts", () -> "3");
        registry.add("activation.worker.base-backoff-seconds", () -> "1");
    }

    @Test
    void sucessoDoProvedorLevaSolicitacaoAteCompleted() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(200)));

        UUID id = service.create(
                new CreateActivationRequest("EXT-OK-" + UUID.randomUUID(), "CUST-1", "P1"),
                "cliente-a").request().getId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ActivationRequest r = repository.findById(id).orElseThrow();
            assertThat(r.getStatus()).isEqualTo(RequestStatus.COMPLETED);
            assertThat(r.getAttempts()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void erroPermanenteDoProvedorLevaAFailed() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(400)));

        UUID id = service.create(
                new CreateActivationRequest("EXT-FAIL-" + UUID.randomUUID(), "CUST-1", "P1"),
                "cliente-a").request().getId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ActivationRequest r = repository.findById(id).orElseThrow();
            assertThat(r.getStatus()).isEqualTo(RequestStatus.FAILED);
        });
    }

    @Test
    void erroTransienteEsgotaTentativasEVaiAFailed() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(500)));

        UUID id = service.create(
                new CreateActivationRequest("EXT-RETRY-" + UUID.randomUUID(), "CUST-1", "P1"),
                "cliente-a").request().getId();

        // max-attempts=3, backoff base 1s → alcança FAILED em poucos segundos.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ActivationRequest r = repository.findById(id).orElseThrow();
            assertThat(r.getStatus()).isEqualTo(RequestStatus.FAILED);
            assertThat(r.getAttempts()).isEqualTo(3);
        });
    }
}
