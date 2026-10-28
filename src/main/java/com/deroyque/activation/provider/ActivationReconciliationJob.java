package com.deroyque.activation.provider;

import com.deroyque.activation.domain.ActivationRequestRepository;
import com.deroyque.activation.domain.RequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Resgata solicitações "presas" em PROCESSING.
 *
 * <p>Cenário: a aplicação marca PROCESSING, chama o provedor e cai antes de aplicar o desfecho.
 * O registro ficaria preso em PROCESSING indefinidamente. Este job, após {@code stuckAfter},
 * devolve essas solicitações a PENDING para serem reprocessadas pelo worker. Como enviamos uma
 * chave de idempotência ao provedor, um eventual reenvio não ativa o produto duas vezes.
 */
@Component
public class ActivationReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ActivationReconciliationJob.class);

    private final ActivationRequestRepository repository;
    private final ActivationWorkerProperties properties;

    public ActivationReconciliationJob(ActivationRequestRepository repository,
                                       ActivationWorkerProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${activation.reconciliation.interval}")
    @Transactional
    public void reconcile() {
        if (!properties.worker().enabled()) {
            return;
        }
        Instant threshold = Instant.now().minus(properties.reconciliation().stuckAfter());
        var stuck = repository.findByStatusAndUpdatedAtBefore(RequestStatus.PROCESSING, threshold);
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("Reconciliação: {} solicitação(ões) presa(s) em PROCESSING; devolvendo a PENDING",
                stuck.size());
        Instant now = Instant.now();
        for (var request : stuck) {
            // PROCESSING -> PENDING com reprocessamento imediato.
            request.markForRetry("Reconciliação: processamento não concluído", now);
            repository.save(request);
        }
    }
}
