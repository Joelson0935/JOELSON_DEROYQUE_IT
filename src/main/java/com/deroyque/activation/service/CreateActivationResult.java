package com.deroyque.activation.service;

import com.deroyque.activation.domain.ActivationRequest;

public record CreateActivationResult(ActivationRequest request, boolean created) {

    public static CreateActivationResult created(ActivationRequest request) {
        return new CreateActivationResult(request, true);
    }

    public static CreateActivationResult existing(ActivationRequest request) {
        return new CreateActivationResult(request, false);
    }
}
