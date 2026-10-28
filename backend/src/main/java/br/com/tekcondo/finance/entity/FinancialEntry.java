package br.com.tekcondo.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.supplier.entity.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Título financeiro: conta a pagar (EXPENSE) ou a receber (INCOME). Cobranças condominiais são títulos a receber
 * vinculados a uma unidade. O saldo pago só muda por baixas e estornos registrados.
 */
@Entity
@Table(name = "financial_entry")
public class FinancialEntry {

    public enum Kind {
        INCOME, EXPENSE
    }

    public enum Status {
        OPEN, PARTIAL, PAID, CANCELLED, RENEGOTIATED
    }

    public enum Approval {
        NOT_REQUIRED, PENDING, APPROVED, REJECTED
    }

    public enum Origin {
        MANUAL, RECURRENCE, ALLOCATION, AGREEMENT, IMPORT
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chart_account_id")
    private ChartAccount chartAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "paid_amount", nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalDate competence;

    @Column(name = "document_number")
    private String documentNumber;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Approval approval = Approval.NOT_REQUIRED;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_note")
    private String approvalNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Origin origin = Origin.MANUAL;

    @Column(name = "recurrence_group", updatable = false)
    private UUID recurrenceGroup;

    @Column(updatable = false)
    private Integer installment;

    @Column(updatable = false)
    private Integer installments;

    @Column(name = "allocation_id", updatable = false)
    private UUID allocationId;

    @Column(name = "agreement_id", updatable = false)
    private UUID agreementId;

    @Column(name = "fine_percent")
    private BigDecimal finePercent;

    @Column(name = "interest_monthly_percent")
    private BigDecimal interestMonthlyPercent;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Número de cobrança (gerado pelo banco de dados): enviado ao banco no boleto e usado na baixa pelo retorno. */
    @Generated(event = EventType.INSERT)
    @Column(name = "collection_number", insertable = false, updatable = false)
    private Long collectionNumber;

    @Version
    private Long version;

    protected FinancialEntry() {
    }

    public FinancialEntry(UUID organizationId, UUID condominiumId, Kind kind, Origin origin, UUID createdBy) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.kind = kind;
        this.origin = origin;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------ regras

    public void details(String description, ChartAccount account, CostCenter costCenter, Supplier supplier, Unit unit,
                        Person person, BigDecimal amount, LocalDate dueDate, LocalDate competence,
                        String documentNumber, String notes, UUID by) {
        if (paidAmount.signum() > 0) {
            throw ApiException.businessRule("ENTRY_HAS_PAYMENTS",
                    "O lançamento possui baixas. Estorne as baixas antes de alterá-lo.");
        }
        ensureMutable();
        this.description = description.trim();
        this.chartAccount = account;
        this.costCenter = costCenter;
        this.supplier = supplier;
        this.unit = unit;
        this.person = person;
        this.amount = amount;
        this.dueDate = dueDate;
        this.competence = competence.withDayOfMonth(1);
        this.documentNumber = documentNumber;
        this.notes = notes;
        this.updatedBy = by;
    }

    public void recurrence(UUID group, int installment, int installments) {
        this.recurrenceGroup = group;
        this.installment = installment;
        this.installments = installments;
    }

    public void linkAllocation(UUID allocationId) {
        this.allocationId = allocationId;
    }

    public void linkAgreement(UUID agreementId) {
        this.agreementId = agreementId;
    }

    public void lateFees(BigDecimal finePercent, BigDecimal interestMonthlyPercent) {
        this.finePercent = finePercent;
        this.interestMonthlyPercent = interestMonthlyPercent;
    }

    public void requireApproval(boolean required) {
        this.approval = required ? Approval.PENDING : Approval.NOT_REQUIRED;
    }

    public BigDecimal remaining() {
        return amount.subtract(paidAmount);
    }

    public boolean isOverdue(LocalDate today) {
        return (status == Status.OPEN || status == Status.PARTIAL) && dueDate.isBefore(today);
    }

    public void approve(UUID by, Instant now, String note) {
        if (approval != Approval.PENDING) {
            throw ApiException.businessRule("ENTRY_NOT_PENDING", "Este lançamento não está aguardando aprovação.");
        }
        approval = Approval.APPROVED;
        approvedBy = by;
        approvedAt = now;
        approvalNote = note;
        updatedBy = by;
    }

    public void reject(UUID by, Instant now, String note) {
        if (approval != Approval.PENDING) {
            throw ApiException.businessRule("ENTRY_NOT_PENDING", "Este lançamento não está aguardando aprovação.");
        }
        approval = Approval.REJECTED;
        approvedBy = by;
        approvedAt = now;
        approvalNote = note;
        updatedBy = by;
    }

    /** Registra baixa (parcial ou total) do valor principal. */
    public void registerPayment(BigDecimal principal, UUID by) {
        if (status != Status.OPEN && status != Status.PARTIAL) {
            throw ApiException.businessRule("ENTRY_NOT_PAYABLE", "Somente lançamentos em aberto podem receber baixa.");
        }
        if (approval == Approval.PENDING || approval == Approval.REJECTED) {
            throw ApiException.businessRule("ENTRY_NOT_APPROVED",
                    approval == Approval.PENDING ? "O pagamento aguarda aprovação." : "O pagamento foi reprovado.");
        }
        if (principal.compareTo(remaining()) > 0) {
            throw ApiException.businessRule("PAYMENT_EXCEEDS_BALANCE",
                    "O valor da baixa excede o saldo em aberto (R$ " + remaining().toPlainString() + ").");
        }
        paidAmount = paidAmount.add(principal);
        status = paidAmount.compareTo(amount) == 0 ? Status.PAID : Status.PARTIAL;
        updatedBy = by;
    }

    /** Desfaz o efeito de uma baixa estornada. */
    public void reversePayment(BigDecimal principal, UUID by) {
        if (status == Status.CANCELLED || status == Status.RENEGOTIATED) {
            throw ApiException.businessRule("ENTRY_CLOSED", "O lançamento está encerrado.");
        }
        paidAmount = paidAmount.subtract(principal);
        status = paidAmount.signum() == 0 ? Status.OPEN : Status.PARTIAL;
        updatedBy = by;
    }

    public void cancel(String reason, UUID by, Instant now) {
        if (status == Status.CANCELLED || status == Status.RENEGOTIATED) {
            throw ApiException.businessRule("ENTRY_ALREADY_CLOSED", "O lançamento já está cancelado ou renegociado.");
        }
        if (paidAmount.signum() > 0) {
            throw ApiException.businessRule("ENTRY_HAS_PAYMENTS",
                    "O lançamento possui baixas. Estorne as baixas antes de cancelá-lo.");
        }
        status = Status.CANCELLED;
        cancelReason = reason;
        cancelledBy = by;
        cancelledAt = now;
        updatedBy = by;
    }

    /** Título substituído por acordo de parcelamento. */
    public void renegotiate(UUID agreementId, UUID by) {
        if (status != Status.OPEN && status != Status.PARTIAL) {
            throw ApiException.businessRule("ENTRY_NOT_OPEN", "Somente títulos em aberto podem ser renegociados.");
        }
        status = Status.RENEGOTIATED;
        cancelReason = "Renegociado no acordo " + agreementId;
        updatedBy = by;
    }

    /** Desfaz a renegociação (acordo cancelado sem pagamentos): o título volta a ficar em aberto. */
    public void restoreFromAgreement(UUID by) {
        if (status != Status.RENEGOTIATED) {
            return;
        }
        status = paidAmount.signum() == 0 ? Status.OPEN : Status.PARTIAL;
        cancelReason = null;
        updatedBy = by;
    }

    private void ensureMutable() {
        if (status == Status.CANCELLED || status == Status.RENEGOTIATED || status == Status.PAID) {
            throw ApiException.businessRule("ENTRY_CLOSED", "Lançamento encerrado não pode ser alterado.");
        }
    }

    // ------------------------------------------------------------------ getters

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public ChartAccount getChartAccount() {
        return chartAccount;
    }

    public CostCenter getCostCenter() {
        return costCenter;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public Unit getUnit() {
        return unit;
    }

    public Person getPerson() {
        return person;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getCompetence() {
        return competence;
    }

    public Long getCollectionNumber() {
        return collectionNumber;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getNotes() {
        return notes;
    }

    public Status getStatus() {
        return status;
    }

    public Approval getApproval() {
        return approval;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public String getApprovalNote() {
        return approvalNote;
    }

    public Origin getOrigin() {
        return origin;
    }

    public UUID getRecurrenceGroup() {
        return recurrenceGroup;
    }

    public Integer getInstallment() {
        return installment;
    }

    public Integer getInstallments() {
        return installments;
    }

    public UUID getAllocationId() {
        return allocationId;
    }

    public UUID getAgreementId() {
        return agreementId;
    }

    public BigDecimal getFinePercent() {
        return finePercent;
    }

    public BigDecimal getInterestMonthlyPercent() {
        return interestMonthlyPercent;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
