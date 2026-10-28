package br.com.tekcondo.finance.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Histórico de fechamentos e reaberturas do período financeiro. Somente inclusão. */
@Entity
@Table(name = "period_closing")
public class PeriodClosing {

    public enum Action {
        CLOSE, REOPEN
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private Action action;

    @Column(name = "closed_until", updatable = false)
    private LocalDate closedUntil;

    @Column(name = "previous_until", updatable = false)
    private LocalDate previousUntil;

    @Column(name = "notes", updatable = false)
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", updatable = false)
    private Map<String, Object> snapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    protected PeriodClosing() {
    }

    public PeriodClosing(UUID organizationId, UUID condominiumId, Action action, LocalDate closedUntil, LocalDate previousUntil,
                         String notes, Map<String, Object> snapshot, UUID createdBy, String createdByName) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.action = action;
        this.closedUntil = closedUntil;
        this.previousUntil = previousUntil;
        this.notes = notes;
        this.snapshot = snapshot;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Action getAction() {
        return action;
    }

    public LocalDate getClosedUntil() {
        return closedUntil;
    }

    public LocalDate getPreviousUntil() {
        return previousUntil;
    }

    public String getNotes() {
        return notes;
    }

    public Map<String, Object> getSnapshot() {
        return snapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
