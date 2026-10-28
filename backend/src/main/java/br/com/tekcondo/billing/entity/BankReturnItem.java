package br.com.tekcondo.billing.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.integration.bank.Cnab240ReturnParser;
import br.com.tekcondo.shared.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Título informado no retorno do banco. SETTLED = baixado automaticamente; PENDING = liquidação que não pôde ser
 * baixada (título não encontrado, já pago, valor divergente) e aguarda tratamento; INFO = movimento sem efeito
 * financeiro (entrada confirmada, baixa, tarifa); IGNORED = pendência tratada manualmente.
 */
@Entity
@Table(name = "bank_return_item")
public class BankReturnItem {

    public enum Status {
        SETTLED, PENDING, INFO, IGNORED
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID bankAccountId;

    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Column(name = "movement_code", nullable = false, updatable = false)
    private String movementCode;

    @Column(name = "our_number", updatable = false)
    private String ourNumber;

    @Column(name = "your_number", updatable = false)
    private String yourNumber;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Column(name = "face_value", nullable = false, updatable = false)
    private BigDecimal faceValue;

    @Column(nullable = false, updatable = false)
    private BigDecimal fees;

    @Column(nullable = false, updatable = false)
    private BigDecimal discount;

    @Column(name = "paid_amount", nullable = false, updatable = false)
    private BigDecimal paidAmount;

    @Column(name = "bank_fee", nullable = false, updatable = false)
    private BigDecimal bankFee;

    @Column(name = "occurrence_date", updatable = false)
    private LocalDate occurrenceDate;

    @Column(name = "credit_date", updatable = false)
    private LocalDate creditDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "payment_id")
    private UUID paymentId;

    private String message;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    protected BankReturnItem() {
    }

    public BankReturnItem(UUID organizationId, UUID condominiumId, UUID bankAccountId, UUID fileId, Cnab240ReturnParser.Item i) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.bankAccountId = bankAccountId;
        this.fileId = fileId;
        this.sequence = i.sequence();
        this.movementCode = i.movementCode();
        this.ourNumber = i.ourNumber();
        this.yourNumber = i.yourNumber();
        this.dueDate = i.dueDate();
        this.faceValue = i.faceValue();
        this.fees = i.feesAndCharges();
        this.discount = i.discount();
        this.paidAmount = i.paidAmount();
        this.bankFee = i.bankFee();
        this.occurrenceDate = i.occurrenceDate();
        this.creditDate = i.creditDate();
        this.status = i.isSettlement() ? Status.PENDING : Status.INFO;
    }

    public void settled(UUID entryId, UUID paymentId) {
        this.status = Status.SETTLED;
        this.entryId = entryId;
        this.paymentId = paymentId;
        this.message = null;
    }

    public void pending(UUID entryId, String message) {
        this.status = Status.PENDING;
        this.entryId = entryId;
        this.message = message;
    }

    public void info(String message) {
        this.message = message;
    }

    public void ignore(String note, UUID by, Instant now) {
        if (status != Status.PENDING) {
            throw ApiException.businessRule("RETURN_ITEM_NOT_PENDING", "Somente pendências podem ser descartadas.");
        }
        status = Status.IGNORED;
        message = note;
        resolvedBy = by;
        resolvedAt = now;
    }

    /** Data em que o valor entrou na conta (crédito); na falta, a data da ocorrência. */
    public LocalDate settlementDate() {
        return creditDate != null ? creditDate : occurrenceDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public int getSequence() {
        return sequence;
    }

    public String getMovementCode() {
        return movementCode;
    }

    public String getOurNumber() {
        return ourNumber;
    }

    public String getYourNumber() {
        return yourNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getFaceValue() {
        return faceValue;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getBankFee() {
        return bankFee;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public LocalDate getCreditDate() {
        return creditDate;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getMessage() {
        return message;
    }
}
