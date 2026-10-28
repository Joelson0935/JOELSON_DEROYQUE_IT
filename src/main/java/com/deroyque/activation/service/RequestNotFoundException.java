package com.deroyque.activation.service;

import java.util.UUID;

public class RequestNotFoundException extends RuntimeException {

    public RequestNotFoundException(UUID id) {
        super("Solicitação não encontrada: " + id);
    }
}
