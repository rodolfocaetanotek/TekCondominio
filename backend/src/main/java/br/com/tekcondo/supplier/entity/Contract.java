package br.com.tekcondo.supplier.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Contrato de prestação de serviço/fornecimento vinculado ao condomínio. */
@Entity
@Table(name = "contract")
public class Contract extends AuditableEntity {

    public enum Periodicity {
        MONTHLY, YEARLY, ONE_TIME, OTHER
    }

    public enum Status {
        ACTIVE, TERMINATED
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", nullable = false)
    private Periodicity periodicity;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "readjustment_index")
    private String readjustmentIndex;

    @Column(name = "readjustment_month")
    private Integer readjustmentMonth;

    @Column(name = "notice_days", nullable = false)
    private int noticeDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "terminated_on")
    private LocalDate terminatedOn;

    @Column(name = "termination_reason")
    private String terminationReason;

    protected Contract() {
    }

    public Contract(UUID organizationId, UUID condominiumId) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
    }

    public void update(Supplier supplier, String title, String description, BigDecimal amount, Periodicity periodicity,
                       LocalDate startDate, LocalDate endDate, String readjustmentIndex, Integer readjustmentMonth, int noticeDays) {
        if (status == Status.TERMINATED) {
            throw ApiException.regraNegocio("CONTRACT_TERMINATED", "Contrato encerrado não pode ser alterado.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw ApiException.regraNegocio("CONTRACT_DATES", "O término deve ser posterior ao início.");
        }
        this.supplier = supplier;
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.periodicity = periodicity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.readjustmentIndex = readjustmentIndex;
        this.readjustmentMonth = readjustmentMonth;
        this.noticeDays = noticeDays;
    }

    public void terminate(LocalDate on, String reason) {
        if (status == Status.TERMINATED) {
            throw ApiException.regraNegocio("CONTRACT_TERMINATED", "O contrato já está encerrado.");
        }
        status = Status.TERMINATED;
        terminatedOn = on;
        terminationReason = reason;
    }

    /** Vencido: ativo e com término no passado. A vencer: término dentro do prazo de aviso prévio. */
    public boolean isExpired(LocalDate today) {
        return status == Status.ACTIVE && endDate != null && endDate.isBefore(today);
    }

    public boolean isExpiring(LocalDate today) {
        return status == Status.ACTIVE && endDate != null && !endDate.isBefore(today) && !endDate.isAfter(today.plusDays(noticeDays));
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Periodicity getPeriodicity() {
        return periodicity;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReadjustmentIndex() {
        return readjustmentIndex;
    }

    public Integer getReadjustmentMonth() {
        return readjustmentMonth;
    }

    public int getNoticeDays() {
        return noticeDays;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getTerminatedOn() {
        return terminatedOn;
    }

    public String getTerminationReason() {
        return terminationReason;
    }
}
