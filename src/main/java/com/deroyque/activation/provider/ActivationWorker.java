package com.deroyque.activation.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
            
            List<ActivateCommand> batch = stateUpdater.claimBatch(properties.worker().batchSize());
            if (batch.isEmpty()) {
                return;
            }
            log.debug("Worker processando lote de {} solicitação(ões)", batch.size());
            
            for (ActivateCommand command : batch) {
                try {
                    processor.dispatch(command);
                } catch (Exception e) {
                    
                    log.error("Erro ao processar solicitação id={}: {}",
                            command.idempotencyKey(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Falha no ciclo do worker de ativação: {}", e.getMessage(), e);
        }
    }
}
