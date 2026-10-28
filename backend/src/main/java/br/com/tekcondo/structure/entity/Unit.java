package br.com.tekcondo.structure.entity;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Unidade autônoma (apartamento, casa, sala, loja, vaga...). Fração ideal em percentual (0–100, 6 casas). */
@Entity
@Table(name = "unit")
public class Unit extends AuditableEntity {

    public enum Type {
        APARTMENT, HOUSE, ROOM, STORE, PARKING, OTHER
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Type type;

    @Column(name = "floor")
    private String floor;

    @Column(name = "area")
    private BigDecimal area;

    @Column(name = "ideal_fraction", nullable = false)
    private BigDecimal idealFraction = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Unit() {
    }

    public Unit(UUID organizationId, UUID condominiumId) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
    }

    public void update(Building building, String identifier, Type type, String floor, BigDecimal area,
                       BigDecimal idealFraction, String notes) {
        this.building = building;
        this.identifier = identifier.trim();
        this.type = type;
        this.floor = blank(floor);
        this.area = area;
        this.idealFraction = idealFraction == null ? BigDecimal.ZERO : idealFraction;
        this.notes = blank(notes);
    }

    /** Rótulo legível: "Bloco A · 101" ou "101". */
    public String label() {
        return building == null ? identifier : building.getName() + " · " + identifier;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public Building getBuilding() {
        return building;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Type getType() {
        return type;
    }

    public String getFloor() {
        return floor;
    }

    public BigDecimal getArea() {
        return area;
    }

    public BigDecimal getIdealFraction() {
        return idealFraction;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
