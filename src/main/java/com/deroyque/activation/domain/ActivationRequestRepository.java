package com.deroyque.activation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivationRequestRepository
        extends JpaRepository<ActivationRequest, UUID>, JpaSpecificationExecutor<ActivationRequest> {

    Optional<ActivationRequest> findByExternalId(String externalId);

    Page<ActivationRequest> findAll(
            org.springframework.data.jpa.domain.Specification<ActivationRequest> spec, Pageable pageable);

    /**
     * Busca solicitações PENDING elegíveis para processamento pelo worker de outbox.
     * Usa {@code FOR UPDATE SKIP LOCKED} para permitir múltiplas instâncias concorrentes
     * sem processar o mesmo registro duas vezes (ver seção de cloud no README).
     */
    @Query(value = """
            SELECT * FROM activation_request
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ActivationRequest> lockPendingBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    /**
     * Solicitações presas em PROCESSING além do limiar informado (candidatas a reconciliação).
     */
    List<ActivationRequest> findByStatusAndUpdatedAtBefore(RequestStatus status, Instant threshold);
}
