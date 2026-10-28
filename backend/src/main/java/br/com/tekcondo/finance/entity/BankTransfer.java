package br.com.tekcondo.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_transfer")
public class BankTransfer {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", updatable = false)
    private BankAccount fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", updatable = false)
    private BankAccount toAccount;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "transfer_date", nullable = false, updatable = false)
    private LocalDate transferDate;

    private String description;

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

    protected BankTransfer() {
    }

    public BankTransfer(UUID organizationId, UUID condominiumId, BankAccount from, BankAccount to, BigDecimal amount,
                        LocalDate date, String description, UUID createdBy) {
        if (from.getId().equals(to.getId())) {
            throw ApiException.businessRule("SAME_ACCOUNT", "As contas de origem e destino devem ser diferentes.");
        }
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.fromAccount = from;
        this.toAccount = to;
        this.amount = amount;
        this.transferDate = date;
        this.description = description;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void reverse(String reason, UUID by, Instant now) {
        if (reversedAt != null) {
            throw ApiException.businessRule("TRANSFER_ALREADY_REVERSED", "Esta transferência já foi estornada.");
        }
        if (reconciledAt != null) {
            throw ApiException.businessRule("TRANSFER_RECONCILED", "Desfaça a conciliação antes de estornar.");
        }
        reversedAt = now;
        reversedBy = by;
        reversalReason = reason;
    }

    public void reconcile(boolean reconciled, UUID by, Instant now) {
        if (reversedAt != null) {
            throw ApiException.businessRule("TRANSFER_REVERSED", "Transferência estornada não pode ser conciliada.");
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

    public BankAccount getFromAccount() {
        return fromAccount;
    }

    public BankAccount getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public String getDescription() {
        return description;
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
}
