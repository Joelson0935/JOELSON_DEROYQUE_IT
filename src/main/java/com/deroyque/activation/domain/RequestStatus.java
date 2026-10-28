package com.deroyque.activation.domain;

import java.util.Set;

public enum RequestStatus {

    PENDING,

    PROCESSING,

    COMPLETED,

    FAILED;

    private static final java.util.Map<RequestStatus, Set<RequestStatus>> TRANSITIONS = java.util.Map.of(
            PENDING, Set.of(PROCESSING),
            
            PROCESSING, Set.of(COMPLETED, FAILED, PENDING),
            COMPLETED, Set.of(),
            FAILED, Set.of()
    );

    public boolean canTransitionTo(RequestStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
