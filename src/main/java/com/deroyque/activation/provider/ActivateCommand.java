package com.deroyque.activation.provider;

import java.util.UUID;

public record ActivateCommand(
        UUID idempotencyKey,
        String externalId,
        String customerId,
        String productCode
) {
}
