package com.deroyque.activation.service;

import com.deroyque.activation.domain.ActivationRequest;
import com.deroyque.activation.domain.RequestStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specifications para a listagem paginada com filtros opcionais.
 */
public final class ActivationRequestSpecifications {

    private ActivationRequestSpecifications() {
    }

    public static Specification<ActivationRequest> withFilters(
            RequestStatus status, String customerId, String consumerId) {

        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null && !customerId.isBlank()) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            // Isolamento por consumidor (BOLA): um consumidor só enxerga o que ele criou.
            if (consumerId != null && !consumerId.isBlank()) {
                predicates.add(cb.equal(root.get("consumerId"), consumerId));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
