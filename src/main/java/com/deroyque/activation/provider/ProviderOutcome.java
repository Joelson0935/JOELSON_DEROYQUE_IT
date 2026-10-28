package com.deroyque.activation.provider;

/**
 * Resultado de uma chamada ao provedor externo, classificado segundo a decisão de negócio.
 *
 * <p>Mapeamento de cenários (ver README, item 5):
 * <ul>
 *   <li>{@code SUCCESS} (2xx): ativação concluída → COMPLETED, sem nova tentativa.</li>
 *   <li>{@code PERMANENT_FAILURE} (ex.: HTTP 400): erro do nosso lado/dados inválidos →
 *       FAILED, sem nova tentativa (retry não resolveria).</li>
 *   <li>{@code TRANSIENT_FAILURE} (HTTP 5xx, timeout, 429/503): condição temporária →
 *       permanece elegível a retry com backoff até o limite de tentativas.</li>
 * </ul>
 */
public record ProviderOutcome(Type type, String detail, Integer retryAfterSeconds) {

    public enum Type {
        SUCCESS,
        PERMANENT_FAILURE,
        TRANSIENT_FAILURE
    }

    public static ProviderOutcome success() {
        return new ProviderOutcome(Type.SUCCESS, null, null);
    }

    public static ProviderOutcome permanent(String detail) {
        return new ProviderOutcome(Type.PERMANENT_FAILURE, detail, null);
    }

    public static ProviderOutcome transient_(String detail) {
        return new ProviderOutcome(Type.TRANSIENT_FAILURE, detail, null);
    }

    public static ProviderOutcome transient_(String detail, Integer retryAfterSeconds) {
        return new ProviderOutcome(Type.TRANSIENT_FAILURE, detail, retryAfterSeconds);
    }

    public boolean isSuccess() {
        return type == Type.SUCCESS;
    }
}
