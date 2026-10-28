package br.com.tekcondo.billing.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Arquivo de retorno de cobrança processado. Somente inclusão. */
@Entity
@Table(name = "bank_return_file")
public class BankReturnFile {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID bankAccountId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "file_hash", nullable = false, updatable = false)
    private String fileHash;

    @Column(name = "bank_code", nullable = false, updatable = false)
    private String bankCode;

    @Column(name = "generated_on", updatable = false)
    private LocalDate generatedOn;

    @Column(name = "items_total", nullable = false)
    private int itemsTotal;

    @Column(name = "settled", nullable = false)
    private int settled;

    @Column(name = "pending", nullable = false)
    private int pending;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    protected BankReturnFile() {
    }

    public BankReturnFile(UUID organizationId, UUID condominiumId, UUID bankAccountId, String fileName, String fileHash,
                          String bankCode, LocalDate generatedOn, int itemsTotal, UUID createdBy, String createdByName) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.bankAccountId = bankAccountId;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.bankCode = bankCode;
        this.generatedOn = generatedOn;
        this.itemsTotal = itemsTotal;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void result(int settled, int pending) {
        this.settled = settled;
        this.pending = pending;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getBankCode() {
        return bankCode;
    }

    public LocalDate getGeneratedOn() {
        return generatedOn;
    }

    public int getItemsTotal() {
        return itemsTotal;
    }

    public int getSettled() {
        return settled;
    }

    public int getPending() {
        return pending;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
