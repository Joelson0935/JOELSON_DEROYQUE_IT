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

    @Query(value = """
            SELECT * FROM activation_request
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ActivationRequest> lockPendingBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    List<ActivationRequest> findByStatusAndUpdatedAtBefore(RequestStatus status, Instant threshold);
}
