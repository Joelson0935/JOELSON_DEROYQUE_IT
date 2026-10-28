package com.deroyque.activation.provider;

import java.util.UUID;

/**
 * Comando enviado ao provedor externo.
 *
 * <p>O {@code idempotencyKey} é o próprio id da solicitação. Em um timeout, não sabemos se o
 * provedor processou ou não; ao reenviar com a mesma chave, um provedor idempotente evita
 * ativar o produto duas vezes.
 */
public record ActivateCommand(
        UUID idempotencyKey,
        String externalId,
        String customerId,
        String productCode
) {
}
