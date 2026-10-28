package com.deroyque.activation.api.dto;

import com.deroyque.activation.domain.ActivationRequest;
import com.deroyque.activation.domain.RequestStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação de saída de uma solicitação de ativação.
 *
 * <p>As datas são serializadas como {@link Instant} (UTC, ISO-8601) — ex.:
 * {@code 2026-10-26T14:03:11Z}.
 */
public record ActivationResponse(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        RequestStatus status,
        int attempts,
        Instant createdAt,
        Instant updatedAt
) {

    public static ActivationResponse from(ActivationRequest entity) {
        return new ActivationResponse(
                entity.getId(),
                entity.getExternalId(),
                entity.getCustomerId(),
                entity.getProductCode(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
