package com.deroyque.activation.domain;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(RequestStatus from, RequestStatus to) {
        super("Transição de status inválida: %s -> %s".formatted(from, to));
    }
}
