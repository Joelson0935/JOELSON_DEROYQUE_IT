package com.deroyque.activation.provider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.deroyque.activation.domain.ActivationRequestRepository;
import com.deroyque.activation.domain.RequestStatus;

/**
 * Transições de estado persistidas em transações curtas e independentes.
 *
 * <p>Fica num bean separado de {@link ActivationProcessor} de propósito: métodos
 * {@code @Transactional} só têm efeito quando invocados através do proxy do Spring. Se
 * estivessem no mesmo bean do orquestrador (self-invocation), as transações seriam ignoradas.
 */
@Component
public class ActivationStateUpdater {

    private static final Logger log = LoggerFactory.getLogger(ActivationStateUpdater.class);

    private final ActivationRequestRepository repository;
    private final ActivationWorkerProperties properties;

    public ActivationStateUpdater(ActivationRequestRepository repository,
                                  ActivationWorkerProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Seleciona um lote de PENDING elegíveis (com {@code FOR UPDATE SKIP LOCKED}) e marca todos
     * como PROCESSING dentro da mesma transação. O lock + SKIP LOCKED garante que instâncias
     * concorrentes peguem lotes disjuntos. Retorna os comandos a enviar ao provedor.
     */
    @Transactional
    public java.util.List<ActivateCommand> claimBatch(int batchSize) {
        var batch = repository.lockPendingBatch(Instant.now(), batchSize);
        var commands = new java.util.ArrayList<ActivateCommand>(batch.size());
        for (var request : batch) {
            request.markProcessing();
            repository.save(request);
            commands.add(new ActivateCommand(
                    request.getId(), request.getExternalId(),
                    request.getCustomerId(), request.getProductCode()));
        }
        return commands;
    }

    /** PENDING -> PROCESSING de um único id. Retorna o comando, ou vazio se inelegível. */
    @Transactional
    public Optional<ActivateCommand> beginProcessing(UUID id) {
        var request = repository.findById(id).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.PENDING) {
            return Optional.empty();
        }
        request.markProcessing();
        repository.save(request);
        return Optional.of(new ActivateCommand(
                request.getId(), request.getExternalId(),
                request.getCustomerId(), request.getProductCode()));
    }

    /** Aplica o desfecho da chamada ao provedor (COMPLETED / FAILED / reagendar). */
    @Transactional
    public void applyOutcome(UUID id, ProviderOutcome outcome) {
        var request = repository.findById(id).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.PROCESSING) {
            return;
        }

        switch (outcome.type()) {
            case SUCCESS -> {
                request.markCompleted();
                log.info("Ativação concluída id={}", id);
            }
            case PERMANENT_FAILURE -> {
                request.markFailed(outcome.detail());
                log.warn("Ativação falhou definitivamente id={} motivo={}", id, outcome.detail());
            }
            case TRANSIENT_FAILURE -> {
                if (request.getAttempts() >= properties.worker().maxAttempts()) {
                    request.markFailed("Limite de tentativas atingido: " + outcome.detail());
                    log.warn("Ativação esgotou tentativas id={} ({} tentativas)", id, request.getAttempts());
                } else {
                    Instant next = nextBackoff(request.getAttempts(), outcome.retryAfterSeconds());
                    request.markForRetry(outcome.detail(), next);
                    log.info("Ativação reagendada id={} (tentativa {}) próxima={}",
                            id, request.getAttempts(), next);
                }
            }
        }
        repository.save(request);
    }

    /**
     * Backoff exponencial com teto. {@code Retry-After} do provedor, se presente, tem prioridade.
     * Fórmula: {@code base * 2^(attempts-1)}, limitado a {@code maxBackoffSeconds}.
     */
    private Instant nextBackoff(int attempts, Integer retryAfterSeconds) {
        long seconds;
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            seconds = retryAfterSeconds;
        } else {
            int base = properties.worker().baseBackoffSeconds();
            int max = properties.worker().maxBackoffSeconds();
            long exp = base * (long) Math.pow(2, Math.max(0, attempts - 1));
            seconds = Math.min(exp, max);
        }
        return Instant.now().plusSeconds(seconds);
    }
}
