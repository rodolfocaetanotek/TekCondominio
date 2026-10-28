package br.com.tekcondo.condominium.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "condominium")
public class Condominium extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "cnpj")
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CondominiumType type;

    @Embedded
    private Address address;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CondominiumStatus status = CondominiumStatus.ACTIVE;

    @Column(name = "inactivated_at")
    private Instant inactivatedAt;

    @Column(name = "inactivation_reason")
    private String inactivationReason;

    @Column(name = "fine_percent", nullable = false)
    private java.math.BigDecimal finePercent = new java.math.BigDecimal("2.00");

    @Column(name = "interest_monthly_percent", nullable = false)
    private java.math.BigDecimal interestMonthlyPercent = new java.math.BigDecimal("1.00");

    @Column(name = "billing_due_day", nullable = false)
    private int billingDueDay = 10;

    @Column(name = "require_payable_approval", nullable = false)
    private boolean requirePayableApproval = true;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected Condominium() {
    }

    public Condominium(UUID organizationId, UUID createdBy) {
        this.organizationId = organizationId;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    /** Atualiza os dados cadastrais. Condomínio inativo deve ser reativado antes de ser editado. */
    public void updateDetails(String name, String legalName, String cnpj, CondominiumType type, Address address,
                              String email, String phone, UUID updatedBy) {
        if (status == CondominiumStatus.INACTIVE && getVersion() != null) {
            throw ApiException.businessRule("CONDOMINIUM_INACTIVE",
                    "Condomínio inativo não pode ser editado. Reative-o antes de alterar os dados.");
        }
        this.name = name;
        this.legalName = legalName;
        this.cnpj = cnpj;
        this.type = type;
        this.address = address;
        this.email = email;
        this.phone = phone;
        this.updatedBy = updatedBy;
    }

    public void inactivate(String reason, Instant now, UUID by) {
        if (status == CondominiumStatus.INACTIVE) {
            throw ApiException.businessRule("CONDOMINIUM_ALREADY_INACTIVE", "O condomínio já está inativo.");
        }
        status = CondominiumStatus.INACTIVE;
        inactivatedAt = now;
        inactivationReason = reason;
        updatedBy = by;
    }

    public void activate(UUID by) {
        if (status == CondominiumStatus.ACTIVE) {
            throw ApiException.businessRule("CONDOMINIUM_ALREADY_ACTIVE", "O condomínio já está ativo.");
        }
        status = CondominiumStatus.ACTIVE;
        inactivatedAt = null;
        inactivationReason = null;
        updatedBy = by;
    }

    /** Parâmetros de cobrança e de contas a pagar (premissas P01/P02, configuráveis por condomínio). */
    public void updateFinancialSettings(java.math.BigDecimal finePercent, java.math.BigDecimal interestMonthlyPercent,
                                        int billingDueDay, boolean requirePayableApproval, UUID by) {
        if (finePercent.signum() < 0 || finePercent.compareTo(new java.math.BigDecimal("2")) > 0) {
            throw ApiException.businessRule("FINE_LIMIT",
                    "A multa por atraso da cota condominial é limitada a 2% (Código Civil, art. 1.336, §1º).");
        }
        this.finePercent = finePercent;
        this.interestMonthlyPercent = interestMonthlyPercent;
        this.billingDueDay = billingDueDay;
        this.requirePayableApproval = requirePayableApproval;
        this.updatedBy = by;
    }

    public java.math.BigDecimal getFinePercent() {
        return finePercent;
    }

    public java.math.BigDecimal getInterestMonthlyPercent() {
        return interestMonthlyPercent;
    }

    public int getBillingDueDay() {
        return billingDueDay;
    }

    public boolean isRequirePayableApproval() {
        return requirePayableApproval;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getCnpj() {
        return cnpj;
    }

    public CondominiumType getType() {
        return type;
    }

    public Address getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public CondominiumStatus getStatus() {
        return status;
    }

    public Instant getInactivatedAt() {
        return inactivatedAt;
    }

    public String getInactivationReason() {
        return inactivationReason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
