package br.com.tekcondo.finance.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Arquivo de extrato importado (OFX). Somente inclusão. */
@Entity
@Table(name = "bank_statement_import")
public class StatementImport {

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

    @Column(name = "period_start", updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", updatable = false)
    private LocalDate periodEnd;

    @Column(name = "lines_total", nullable = false)
    private int linesTotal;

    @Column(name = "lines_new", nullable = false)
    private int linesNew;

    @Column(name = "auto_matched", nullable = false)
    private int autoMatched;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    protected StatementImport() {
    }

    public StatementImport(UUID organizationId, UUID condominiumId, UUID bankAccountId, String fileName, String fileHash,
                           LocalDate periodStart, LocalDate periodEnd, int linesTotal, UUID createdBy, String createdByName) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.bankAccountId = bankAccountId;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.linesTotal = linesTotal;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void result(int linesNew, int autoMatched) {
        this.linesNew = linesNew;
        this.autoMatched = autoMatched;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public int getLinesTotal() {
        return linesTotal;
    }

    public int getLinesNew() {
        return linesNew;
    }

    public int getAutoMatched() {
        return autoMatched;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
