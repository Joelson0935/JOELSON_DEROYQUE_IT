package com.deroyque.activation.provider;

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
