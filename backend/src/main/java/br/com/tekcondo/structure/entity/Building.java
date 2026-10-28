package br.com.tekcondo.structure.entity;

import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Bloco ou torre do condomínio. */
@Entity
@Table(name = "building")
public class Building extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Building() {
    }

    public Building(UUID organizationId, UUID condominiumId, String name, String description) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        update(name, description);
    }

    public void update(String name, String description) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
