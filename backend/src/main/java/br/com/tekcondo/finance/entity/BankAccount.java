package br.com.tekcondo.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Conta bancária ou caixa do condomínio. O saldo é sempre calculado a partir das movimentações. */
@Entity
@Table(name = "bank_account")
public class BankAccount extends AuditableEntity {

    public enum Kind {
        CHECKING, SAVINGS, INVESTMENT, CASH
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "bank_code")
    private String bankCode;

    private String agency;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "opening_balance", nullable = false, updatable = false)
    private BigDecimal openingBalance;

    @Column(name = "opening_date", nullable = false, updatable = false)
    private LocalDate openingDate;

    @Column(nullable = false)
    private boolean active = true;

    protected BankAccount() {
    }

    public BankAccount(UUID organizationId, UUID condominiumId, Kind kind, BigDecimal openingBalance, LocalDate openingDate) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.kind = kind;
        this.openingBalance = openingBalance;
        this.openingDate = openingDate;
    }

    /** Saldo inicial e data de abertura não mudam após o cadastro (integridade do histórico). */
    public void update(String name, String bankCode, String agency, String accountNumber) {
        this.name = name.trim();
        this.bankCode = bankCode;
        this.agency = agency;
        this.accountNumber = accountNumber;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getAgency() {
        return agency;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public boolean isActive() {
        return active;
    }
}
