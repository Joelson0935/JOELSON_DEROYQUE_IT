package com.deroyque.activation.service;

import java.util.UUID;

/** Solicitação inexistente (resulta em HTTP 404). */
public class RequestNotFoundException extends RuntimeException {

    public RequestNotFoundException(UUID id) {
        super("Solicitação não encontrada: " + id);
    }
}
