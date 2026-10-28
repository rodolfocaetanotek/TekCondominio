package br.com.tekcondo.people.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.structure.entity.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Vínculo pessoa ↔ unidade com período. Encerrado com data e motivo; nunca apagado (histórico de ocupação). */
@Entity
@Table(name = "unit_occupancy")
public class Occupancy {

    public enum Role {
        OWNER, TENANT, RESIDENT
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id")
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "financial_responsible", nullable = false)
    private boolean financialResponsible;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "end_reason")
    private String endReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Occupancy() {
    }

    public Occupancy(Unit unit, Person person, Role role, boolean financialResponsible, LocalDate startDate,
                     UUID createdBy) {
        this.organizationId = unit.getOrganizationId();
        this.unit = unit;
        this.person = person;
        this.role = role;
        this.financialResponsible = financialResponsible;
        this.startDate = startDate;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isCurrent() {
        return endDate == null;
    }

    public void end(LocalDate date, String reason) {
        if (endDate != null) {
            throw ApiException.businessRule("OCCUPANCY_ALREADY_ENDED", "Este vínculo já foi encerrado.");
        }
        if (date.isBefore(startDate)) {
            throw ApiException.businessRule("OCCUPANCY_END_BEFORE_START",
                    "A data de encerramento não pode ser anterior ao início do vínculo.");
        }
        this.endDate = date;
        this.endReason = reason;
        this.financialResponsible = false;
    }

    public void setFinancialResponsible(boolean financialResponsible) {
        this.financialResponsible = financialResponsible;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Unit getUnit() {
        return unit;
    }

    public Person getPerson() {
        return person;
    }

    public Role getRole() {
        return role;
    }

    public boolean isFinancialResponsible() {
        return financialResponsible;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getEndReason() {
        return endReason;
    }
}
