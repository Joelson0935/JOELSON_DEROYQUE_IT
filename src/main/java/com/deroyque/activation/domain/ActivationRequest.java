package com.deroyque.activation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "activation_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_activation_request_external_id", columnNames = "external_id")
)
public class ActivationRequest {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_id", nullable = false, updatable = false, length = 100)
    private String externalId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "consumer_id", length = 150)
    private String consumerId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivationRequest() {
    }

    private ActivationRequest(String externalId, String customerId, String productCode, String consumerId) {
        this.id = UUID.randomUUID();
        this.externalId = externalId;
        this.customerId = customerId;
        this.productCode = productCode;
        this.consumerId = consumerId;
        this.status = RequestStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = Instant.now();
    }

    public static ActivationRequest create(String externalId, String customerId, String productCode, String consumerId) {
        return new ActivationRequest(externalId, customerId, productCode, consumerId);
    }

    public void transitionTo(RequestStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(this.status, target);
        }
        this.status = target;
    }

    public void markProcessing() {
        transitionTo(RequestStatus.PROCESSING);
        this.attempts++;
    }

    public void markCompleted() {
        transitionTo(RequestStatus.COMPLETED);
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        transitionTo(RequestStatus.FAILED);
        this.failureReason = truncate(reason);
    }

    public void markForRetry(String reason, Instant nextAttemptAt) {
        transitionTo(RequestStatus.PENDING);
        this.failureReason = truncate(reason);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void rescheduleTo(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
