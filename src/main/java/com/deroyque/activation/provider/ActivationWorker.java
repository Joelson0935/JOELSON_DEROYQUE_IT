package com.deroyque.activation.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Worker que consome o "outbox": periodicamente seleciona solicitações PENDING elegíveis
 * (respeitando o backoff via {@code next_attempt_at}) e as processa.
 *
 * <p>O lote é reservado atomicamente com {@code FOR UPDATE SKIP LOCKED}, o que permite rodar
 * várias instâncias da aplicação em paralelo sem processar o mesmo registro duas vezes.
 * As chamadas ao provedor acontecem fora de qualquer transação de banco.
 */
@Component
public class ActivationWorker {

    private static final Logger log = LoggerFactory.getLogger(ActivationWorker.class);

    private final ActivationStateUpdater stateUpdater;
    private final ActivationProcessor processor;
    private final ActivationWorkerProperties properties;

    public ActivationWorker(ActivationStateUpdater stateUpdater,
                            ActivationProcessor processor,
                            ActivationWorkerProperties properties) {
        this.stateUpdater = stateUpdater;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${activation.worker.poll-interval}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }
        try {
            // 1) Reserva e marca o lote como PROCESSING (transação curta).
            List<ActivateCommand> batch = stateUpdater.claimBatch(properties.worker().batchSize());
            if (batch.isEmpty()) {
                return;
            }
            log.debug("Worker processando lote de {} solicitação(ões)", batch.size());
            // 2) Para cada item: chamada remota + aplicação do desfecho (fora de transação longa).
            for (ActivateCommand command : batch) {
                try {
                    processor.dispatch(command);
                } catch (Exception e) {
                    // Um item com erro não deve interromper o lote; permanecerá em PROCESSING
                    // e será resgatado pela reconciliação.
                    log.error("Erro ao processar solicitação id={}: {}",
                            command.idempotencyKey(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Falha no ciclo do worker de ativação: {}", e.getMessage(), e);
        }
    }
}
