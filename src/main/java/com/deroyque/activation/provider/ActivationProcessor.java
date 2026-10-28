package com.deroyque.activation.provider;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class ActivationProcessor {

    private final ActivationStateUpdater stateUpdater;
    private final ActivationProviderClient providerClient;

    public ActivationProcessor(ActivationStateUpdater stateUpdater,
                               ActivationProviderClient providerClient) {
        this.stateUpdater = stateUpdater;
        this.providerClient = providerClient;
    }

    public void process(UUID id) {
        Optional<ActivateCommand> command = stateUpdater.beginProcessing(id);
        if (command.isEmpty()) {
            return; 
        }
        dispatch(command.get());
    }

    public void dispatch(ActivateCommand command) {
        ProviderOutcome outcome = providerClient.activate(command);
        stateUpdater.applyOutcome(command.idempotencyKey(), outcome);
    }
}
