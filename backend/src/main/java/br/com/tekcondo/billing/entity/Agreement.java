package br.com.tekcondo.billing.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Acordo de parcelamento de débitos de uma unidade. */
@Entity
@Table(name = "agreement")
public class Agreement {

    public enum Status {
        ACTIVE, COMPLETED, CANCELLED
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "person_id", updatable = false)
    private UUID personId;

    @Column(name = "original_amount", nullable = false, updatable = false)
    private BigDecimal originalAmount;

    @Column(name = "charges_amount", nullable = false, updatable = false)
    private BigDecimal chargesAmount;

    @Column(name = "discount", nullable = false, updatable = false)
    private BigDecimal discount;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "installments", nullable = false, updatable = false)
    private int installments;

    @Column(name = "first_due_date", nullable = false, updatable = false)
    private LocalDate firstDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "notes")
    private String notes;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Version
    @Column(name = "version")
    private Long version;

    protected Agreement() {
    }

    public Agreement(UUID organizationId, UUID condominiumId, UUID unitId, UUID personId, BigDecimal originalAmount,
                     BigDecimal chargesAmount, BigDecimal discount, int installments, LocalDate firstDueDate,
                     String notes, UUID createdBy) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.unitId = unitId;
        this.personId = personId;
        this.originalAmount = originalAmount;
        this.chargesAmount = chargesAmount;
        this.discount = discount;
        this.totalAmount = originalAmount.add(chargesAmount).subtract(discount);
        if (totalAmount.signum() <= 0) {
            throw ApiException.businessRule("INVALID_AGREEMENT_TOTAL", "O desconto não pode zerar o valor do acordo.");
        }
        this.installments = installments;
        this.firstDueDate = firstDueDate;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void cancel(String reason, UUID by, Instant now) {
        if (status != Status.ACTIVE) {
            throw ApiException.businessRule("AGREEMENT_NOT_ACTIVE", "Somente acordos ativos podem ser cancelados.");
        }
        status = Status.CANCELLED;
        cancelReason = reason;
        cancelledBy = by;
        cancelledAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getPersonId() {
        return personId;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getChargesAmount() {
        return chargesAmount;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getInstallments() {
        return installments;
    }

    public LocalDate getFirstDueDate() {
        return firstDueDate;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
