package br.com.tekcondo.finance.entity;

import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cost_center")
public class CostCenter extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected CostCenter() {
    }

    public CostCenter(UUID organizationId, UUID condominiumId, String name) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.name = name.trim();
    }

    public void rename(String name) {
        this.name = name.trim();
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

    public boolean isActive() {
        return active;
    }
}
