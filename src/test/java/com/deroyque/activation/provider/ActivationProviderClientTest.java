package com.deroyque.activation.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class ActivationProviderClientTest {

    private WireMockServer wireMock;
    private ActivationProviderClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        var properties = new ProviderProperties(
                "http://localhost:" + wireMock.port(),
                "/provider/v1/activate",
                Duration.ofMillis(500),
                Duration.ofMillis(800),
                "test-key");

        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();

        client = new ActivationProviderClient(restClient, properties);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private ActivateCommand command() {
        return new ActivateCommand(UUID.randomUUID(), "EXT-1", "CUST-1", "INTERNET-500");
    }

    @Test
    void sucesso2xxRetornaSuccess() {
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(200)));

        var outcome = client.activate(command());

        assertThat(outcome.type()).isEqualTo(ProviderOutcome.Type.SUCCESS);
    }

    @Test
    void http400RetornaFalhaPermanente() {
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(400)));

        var outcome = client.activate(command());

        assertThat(outcome.type()).isEqualTo(ProviderOutcome.Type.PERMANENT_FAILURE);
    }

    @Test
    void http500RetornaFalhaTransiente() {
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(500)));

        var outcome = client.activate(command());

        assertThat(outcome.type()).isEqualTo(ProviderOutcome.Type.TRANSIENT_FAILURE);
    }

    @Test
    void timeoutRetornaFalhaTransiente() {
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2000))); 

        var outcome = client.activate(command());

        assertThat(outcome.type()).isEqualTo(ProviderOutcome.Type.TRANSIENT_FAILURE);
    }

    @Test
    void http429ComRetryAfterRetornaTransienteComDelay() {
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        var outcome = client.activate(command());

        assertThat(outcome.type()).isEqualTo(ProviderOutcome.Type.TRANSIENT_FAILURE);
        assertThat(outcome.retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void enviaHeaderDeIdempotencia() {
        var cmd = command();
        wireMock.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .withHeader("Idempotency-Key", equalTo(cmd.idempotencyKey().toString()))
                .willReturn(aResponse().withStatus(200)));

        var outcome = client.activate(cmd);

        assertThat(outcome.isSuccess()).isTrue();
    }
}
