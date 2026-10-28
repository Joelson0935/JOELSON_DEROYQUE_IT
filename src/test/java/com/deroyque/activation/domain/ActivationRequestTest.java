package com.deroyque.activation.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários da máquina de estados do domínio (sem Spring, sem banco).
 */
class ActivationRequestTest {

    private ActivationRequest newRequest() {
        return ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", "cliente-a");
    }

    @Test
    void novaSolicitacaoComecaEmPending() {
        var request = newRequest();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.getAttempts()).isZero();
    }

    @Test
    void fluxoFelizPendingProcessingCompleted() {
        var request = newRequest();
        request.markProcessing();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PROCESSING);
        assertThat(request.getAttempts()).isEqualTo(1);

        request.markCompleted();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    void naoPermiteCompletarDiretoDePending() {
        var request = newRequest();
        assertThatThrownBy(request::markCompleted)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void completedNaoVoltaParaProcessing() {
        var request = newRequest();
        request.markProcessing();
        request.markCompleted();

        assertThatThrownBy(() -> request.transitionTo(RequestStatus.PROCESSING))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    void failedEhTerminal() {
        var request = newRequest();
        request.markProcessing();
        request.markFailed("erro");

        assertThat(request.getStatus().isTerminal()).isTrue();
        assertThatThrownBy(() -> request.transitionTo(RequestStatus.PENDING))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void retryVoltaParaPendingAgendandoProximaTentativa() {
        var request = newRequest();
        request.markProcessing();
        Instant next = Instant.now().plusSeconds(30);

        request.markForRetry("falha temporária", next);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.getNextAttemptAt()).isEqualTo(next);
        assertThat(request.getAttempts()).isEqualTo(1); // attempts não é decrementado
    }

    @Test
    void multiplasTentativasIncrementamContador() {
        var request = newRequest();
        request.markProcessing();
        request.markForRetry("falha", Instant.now());
        request.markProcessing();

        assertThat(request.getAttempts()).isEqualTo(2);
    }
}
