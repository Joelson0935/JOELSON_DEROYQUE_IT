package com.deroyque.activation.service;

public class DuplicateExternalIdException extends RuntimeException {

    private final String externalId;

    public DuplicateExternalIdException(String externalId) {
        super("Já existe uma solicitação com externalId '%s' e conteúdo divergente".formatted(externalId));
        this.externalId = externalId;
    }

    public String getExternalId() {
        return externalId;
    }
}
