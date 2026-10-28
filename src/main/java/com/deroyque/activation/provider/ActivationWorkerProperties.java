package com.deroyque.activation.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "activation")
public record ActivationWorkerProperties(
        Worker worker,
        Reconciliation reconciliation,
        Request request
) {

    public record Worker(
            boolean enabled,
            int batchSize,
            long pollInterval,
            int maxAttempts,
            int baseBackoffSeconds,
            int maxBackoffSeconds
    ) {
    }

    public record Reconciliation(
            Duration stuckAfter,
            long interval
    ) {
    }

    public record Request(
            int maxFieldLength
    ) {
    }
}
