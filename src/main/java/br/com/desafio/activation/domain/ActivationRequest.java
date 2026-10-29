package br.com.desafio.activation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "activation_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_activation_requests_external_id",
                        columnNames = "external_id"
                )
        }
)
public class ActivationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_id", nullable = false, length = 100, updatable = false)
    private String externalId;

    @Column(name = "customer_id", nullable = false, length = 100, updatable = false)
    private String customerId;

    @Column(name = "product_code", nullable = false, length = 100, updatable = false)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ActivationRequest() {
    }

    public ActivationRequest(
            String externalId,
            String customerId,
            String productCode
    ) {
        this.externalId = requireText(externalId, "externalId");
        this.customerId = requireText(customerId, "customerId");
        this.productCode = requireText(productCode, "productCode");
        this.status = ActivationStatus.PENDING;
    }

    public void startProcessing() {
        requireStatus(ActivationStatus.PENDING);
        this.status = ActivationStatus.PROCESSING;
    }

    public void complete() {
        requireStatus(ActivationStatus.PROCESSING);
        this.status = ActivationStatus.COMPLETED;
    }

    public void fail() {
        requireStatus(ActivationStatus.PROCESSING);
        this.status = ActivationStatus.FAILED;
    }

    private void requireStatus(ActivationStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Transição inválida: status atual " + this.status
                            + ", esperado " + expected
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }

        if (value.length() > 100) {
            throw new IllegalArgumentException(
                    field + " deve ter no máximo 100 caracteres"
            );
        }

        return value;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
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

    public ActivationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}