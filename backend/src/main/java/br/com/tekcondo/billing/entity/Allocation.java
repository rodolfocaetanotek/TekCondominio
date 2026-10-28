package br.com.tekcondo.billing.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.billing.service.AllocationCalculator;
import br.com.tekcondo.shared.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Rateio de uma competência: origem das cobranças (títulos a receber) de cada unidade. */
@Entity
@Table(name = "allocation")
public class Allocation {

    public enum Status {
        GENERATED, CANCELLED
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "competence", nullable = false, updatable = false)
    private LocalDate competence;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @Column(name = "chart_account_id", nullable = false, updatable = false)
    private UUID chartAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false)
    private AllocationCalculator.Method method;

    @Column(name = "base_amount", nullable = false, updatable = false)
    private BigDecimal baseAmount;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "units_count", nullable = false, updatable = false)
    private int unitsCount;

    @Column(name = "generated_amount", nullable = false, updatable = false)
    private BigDecimal generatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.GENERATED;

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

    protected Allocation() {
    }

    public Allocation(UUID organizationId, UUID condominiumId, LocalDate competence, String description, UUID chartAccountId,
                      AllocationCalculator.Method method, BigDecimal baseAmount, LocalDate dueDate, int unitsCount,
                      BigDecimal generatedAmount, UUID createdBy) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.competence = competence.withDayOfMonth(1);
        this.description = description;
        this.chartAccountId = chartAccountId;
        this.method = method;
        this.baseAmount = baseAmount;
        this.dueDate = dueDate;
        this.unitsCount = unitsCount;
        this.generatedAmount = generatedAmount;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void cancel(String reason, UUID by, Instant now) {
        if (status == Status.CANCELLED) {
            throw ApiException.businessRule("ALLOCATION_ALREADY_CANCELLED", "O rateio já está cancelado.");
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

    public LocalDate getCompetence() {
        return competence;
    }

    public String getDescription() {
        return description;
    }

    public UUID getChartAccountId() {
        return chartAccountId;
    }

    public AllocationCalculator.Method getMethod() {
        return method;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public int getUnitsCount() {
        return unitsCount;
    }

    public BigDecimal getGeneratedAmount() {
        return generatedAmount;
    }

    public Status getStatus() {
        return status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
