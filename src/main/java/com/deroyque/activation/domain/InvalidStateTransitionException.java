package com.deroyque.activation.domain;

/**
 * Lançada quando se tenta uma transição de estado não permitida pela máquina de estados
 * (ex.: tentar concluir uma solicitação já FAILED).
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(RequestStatus from, RequestStatus to) {
        super("Transição de status inválida: %s -> %s".formatted(from, to));
    }
}
