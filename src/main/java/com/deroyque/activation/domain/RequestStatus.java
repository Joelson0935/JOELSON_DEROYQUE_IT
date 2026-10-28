package com.deroyque.activation.domain;

import java.util.Set;

/**
 * Estados possíveis de uma solicitação de ativação e as transições válidas entre eles.
 *
 * <p>A máquina de estados é:
 * <pre>
 *   PENDING ──▶ PROCESSING ──▶ COMPLETED   (terminal)
 *                          └─▶ FAILED       (terminal)
 *   PROCESSING ──▶ PENDING                  (falha temporária: reagendar)
 * </pre>
 *
 * <p>As transições são verificadas no domínio ({@link ActivationRequest}), e não por
 * setters livres, garantindo que, por exemplo, um {@code COMPLETED} nunca retorne a
 * {@code PROCESSING}.
 */
public enum RequestStatus {

    /** Solicitação registrada, aguardando envio ao provedor. */
    PENDING,

    /** Envio ao provedor em andamento. */
    PROCESSING,

    /** Ativação concluída com sucesso. Estado terminal. */
    COMPLETED,

    /** Ativação falhou de forma definitiva. Estado terminal. */
    FAILED;

    private static final java.util.Map<RequestStatus, Set<RequestStatus>> TRANSITIONS = java.util.Map.of(
            PENDING, Set.of(PROCESSING),
            // PROCESSING pode voltar a PENDING em falha temporária (para novo retry agendado)
            PROCESSING, Set.of(COMPLETED, FAILED, PENDING),
            COMPLETED, Set.of(),
            FAILED, Set.of()
    );

    /** Indica se a transição {@code this -> target} é permitida. */
    public boolean canTransitionTo(RequestStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Estado terminal não admite mais transições. */
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
