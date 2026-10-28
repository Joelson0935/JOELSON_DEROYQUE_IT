package com.deroyque.activation.service;

import com.deroyque.activation.domain.ActivationRequest;

/**
 * Resultado da criação de uma solicitação, indicando se um novo registro foi criado
 * (HTTP 201) ou se um retry idempotente devolveu o registro já existente (HTTP 200).
 */
public record CreateActivationResult(ActivationRequest request, boolean created) {

    public static CreateActivationResult created(ActivationRequest request) {
        return new CreateActivationResult(request, true);
    }

    public static CreateActivationResult existing(ActivationRequest request) {
        return new CreateActivationResult(request, false);
    }
}
