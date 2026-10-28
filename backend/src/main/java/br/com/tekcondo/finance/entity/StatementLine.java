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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Transação do extrato do banco e sua situação na conciliação (casada com uma baixa/transferência, ou ignorada). */
@Entity
@Table(name = "bank_statement_line")
public class StatementLine {

    public enum Status {
        UNMATCHED, MATCHED, IGNORED
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID bankAccountId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "fit_id", nullable = false, updatable = false)
    private String fitId;

    @Column(name = "posted_on", nullable = false, updatable = false)
    private LocalDate postedOn;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "trn_type", updatable = false)
    private String trnType;

    @Column(name = "memo", updatable = false)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.UNMATCHED;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "note")
    private String note;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Version
    @Column(name = "version")
    private Long version;

    protected StatementLine() {
    }

    public StatementLine(UUID organizationId, UUID condominiumId, UUID bankAccountId, UUID importId, String fitId,
                         LocalDate postedOn, BigDecimal amount, String trnType, String memo) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.bankAccountId = bankAccountId;
        this.importId = importId;
        this.fitId = fitId;
        this.postedOn = postedOn;
        this.amount = amount;
        this.trnType = trnType;
        this.memo = memo;
    }

    public void matchPayment(UUID paymentId, UUID by, Instant now) {
        ensureUnmatched();
        this.status = Status.MATCHED;
        this.paymentId = paymentId;
        resolved(by, now);
    }

    public void matchTransfer(UUID transferId, UUID by, Instant now) {
        ensureUnmatched();
        this.status = Status.MATCHED;
        this.transferId = transferId;
        resolved(by, now);
    }

    public void ignore(String note, UUID by, Instant now) {
        ensureUnmatched();
        this.status = Status.IGNORED;
        this.note = note;
        resolved(by, now);
    }

    /** Volta a linha para pendente (desfaz casamento ou "ignorar"). */
    public void reset() {
        status = Status.UNMATCHED;
        paymentId = null;
        transferId = null;
        note = null;
        resolvedAt = null;
        resolvedBy = null;
    }

    private void ensureUnmatched() {
        if (status != Status.UNMATCHED) {
            throw ApiException.businessRule("STATEMENT_LINE_RESOLVED", "Esta linha do extrato já foi conciliada ou ignorada.");
        }
    }

    private void resolved(UUID by, Instant now) {
        resolvedBy = by;
        resolvedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public String getFitId() {
        return fitId;
    }

    public LocalDate getPostedOn() {
        return postedOn;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getTrnType() {
        return trnType;
    }

    public String getMemo() {
        return memo;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public String getNote() {
        return note;
    }

    public Long getVersion() {
        return version;
    }
}
