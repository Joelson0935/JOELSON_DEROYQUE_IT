package com.deroyque.activation.provider;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Orquestra o processamento de uma única solicitação.
 *
 * <p><b>Transação x chamada remota.</b> A chamada HTTP ao provedor NÃO ocorre dentro de uma
 * transação de banco aberta — segurar uma conexão durante I/O de rede esgotaria o pool sob
 * carga. O fluxo divide-se em duas transações curtas (delegadas a {@link ActivationStateUpdater},
 * em bean separado para que o proxy transacional do Spring funcione), com a chamada HTTP entre elas.
 *
 * <p><b>Durabilidade (outbox).</b> A solicitação é persistida (PENDING) na criação pela API.
 * Se a aplicação cair entre o commit e a chamada ao provedor, o registro continua PENDING e será
 * reprocessado pelo worker (entrega at-least-once). A idempotência no provedor cobre o reenvio.
 */
@Component
public class ActivationProcessor {

    private final ActivationStateUpdater stateUpdater;
    private final ActivationProviderClient providerClient;

    public ActivationProcessor(ActivationStateUpdater stateUpdater,
                               ActivationProviderClient providerClient) {
        this.stateUpdater = stateUpdater;
        this.providerClient = providerClient;
    }

    /**
     * Processa uma solicitação a partir do zero (seleciona, marca PROCESSING, chama e aplica).
     * Usado pela reconciliação e em cenários pontuais.
     */
    public void process(UUID id) {
        Optional<ActivateCommand> command = stateUpdater.beginProcessing(id);
        if (command.isEmpty()) {
            return; // não estava elegível (ex.: já processado por outra instância)
        }
        dispatch(command.get());
    }

    /**
     * Processa um comando cuja solicitação já foi marcada PROCESSING (ex.: pelo {@code claimBatch}
     * do worker): chama o provedor FORA de transação e aplica o desfecho.
     */
    public void dispatch(ActivateCommand command) {
        ProviderOutcome outcome = providerClient.activate(command);
        stateUpdater.applyOutcome(command.idempotencyKey(), outcome);
    }
}
