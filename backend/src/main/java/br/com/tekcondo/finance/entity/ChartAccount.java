package br.com.tekcondo.finance.entity;

import java.util.UUID;

import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Conta do plano de contas. organizationId nulo = conta do plano padrão do sistema. */
@Entity
@Table(name = "chart_account")
public class ChartAccount extends AuditableEntity {

    public enum Nature {
        INCOME, EXPENSE
    }

    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Nature nature;

    /** Analítica = aceita lançamentos; sintética = apenas agrupa. */
    @Column(nullable = false)
    private boolean analytic = true;

    @Column(nullable = false)
    private boolean active = true;

    protected ChartAccount() {
    }

    public ChartAccount(UUID organizationId, String code, String name, Nature nature) {
        this.organizationId = organizationId;
        this.code = code.trim();
        this.name = name.trim();
        this.nature = nature;
    }

    public void rename(String name) {
        this.name = name.trim();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSystem() {
        return organizationId == null;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Nature getNature() {
        return nature;
    }

    public boolean isAnalytic() {
        return analytic;
    }

    public boolean isActive() {
        return active;
    }
}
