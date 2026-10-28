package br.com.tekcondo.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Baixa de um título. Estorno é lógico (reversedAt + motivo), preservando a trilha. */
@Entity
@Table(name = "entry_payment")
public class EntryPayment {

    public enum Method {
        PIX, BOLETO, TRANSFER, CASH, CHECK, DEBIT, CARD, OTHER
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", updatable = false)
    private FinancialEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", updatable = false)
    private BankAccount bankAccount;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(name = "principal", nullable = false, updatable = false)
    private BigDecimal principal;

    @Column(name = "fine", nullable = false, updatable = false)
    private BigDecimal fine;

    @Column(name = "interest", nullable = false, updatable = false)
    private BigDecimal interest;

    @Column(name = "discount", nullable = false, updatable = false)
    private BigDecimal discount;

    @Column(name = "total", nullable = false, updatable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false)
    private Method method;

    @Column(name = "notes")
    private String notes;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciled_by")
    private UUID reconciledBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected EntryPayment() {
    }

    public EntryPayment(FinancialEntry entry, BankAccount bankAccount, LocalDate paidOn, BigDecimal principal,
                        BigDecimal fine, BigDecimal interest, BigDecimal discount, Method method, String notes,
                        UUID createdBy) {
        this.organizationId = entry.getOrganizationId();
        this.condominiumId = entry.getCondominiumId();
        this.entry = entry;
        this.bankAccount = bankAccount;
        this.paidOn = paidOn;
        this.principal = principal;
        this.fine = fine;
        this.interest = interest;
        this.discount = discount;
        this.total = principal.add(fine).add(interest).subtract(discount);
        if (total.signum() < 0) {
            throw ApiException.businessRule("INVALID_DISCOUNT", "O desconto não pode ser maior que o valor pago.");
        }
        this.method = method;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isReversed() {
        return reversedAt != null;
    }

    public void reverse(String reason, UUID by, Instant now) {
        if (reversedAt != null) {
            throw ApiException.businessRule("PAYMENT_ALREADY_REVERSED", "Esta baixa já foi estornada.");
        }
        if (reconciledAt != null) {
            throw ApiException.businessRule("PAYMENT_RECONCILED",
                    "A baixa está conciliada com o extrato. Desfaça a conciliação antes de estornar.");
        }
        reversedAt = now;
        reversedBy = by;
        reversalReason = reason;
    }

    public void reconcile(boolean reconciled, UUID by, Instant now) {
        if (reversedAt != null) {
            throw ApiException.businessRule("PAYMENT_REVERSED", "Baixa estornada não pode ser conciliada.");
        }
        reconciledAt = reconciled ? now : null;
        reconciledBy = reconciled ? by : null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public FinancialEntry getEntry() {
        return entry;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getFine() {
        return fine;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Method getMethod() {
        return method;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getReconciledAt() {
        return reconciledAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
