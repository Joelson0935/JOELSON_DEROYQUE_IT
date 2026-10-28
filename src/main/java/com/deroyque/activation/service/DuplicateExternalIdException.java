package com.deroyque.activation.service;

/**
 * Já existe uma solicitação com o mesmo {@code externalId} porém com conteúdo divergente
 * (ex.: productCode diferente). Resulta em HTTP 409 Conflict.
 *
 * <p>Observação: um retry legítimo (mesmo externalId + mesmo conteúdo) NÃO lança esta
 * exceção; nesse caso a API responde 200 com o registro já existente (idempotência).
 */
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
