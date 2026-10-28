package br.com.tekcondo.occurrence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "occurrence")
public class Occurrence extends AuditableEntity {

    public enum Category {
        MAINTENANCE, NOISE, SECURITY, CLEANING, COMPLAINT, REQUEST, SUGGESTION, OTHER
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, URGENT
    }

    public enum Status {
        OPEN, IN_PROGRESS, WAITING, RESOLVED, CANCELLED;

        boolean isClosed() {
            return this == RESOLVED || this == CANCELLED;
        }
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "reporter_user_id", updatable = false)
    private UUID reporterUserId;

    @Column(name = "reporter_name", nullable = false, updatable = false)
    private String reporterName;

    @Column(name = "assignee_name")
    private String assigneeName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private String resolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Occurrence() {
    }

    public Occurrence(UUID organizationId, UUID condominiumId, int number, String title, String description,
                      Category category, Priority priority, UUID unitId, UUID reporterUserId, String reporterName) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.number = number;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.unitId = unitId;
        this.reporterUserId = reporterUserId;
        this.reporterName = reporterName;
    }

    public void triage(Category category, Priority priority, String assigneeName, LocalDate dueDate) {
        ensureOpen();
        this.category = category;
        this.priority = priority;
        this.assigneeName = assigneeName;
        this.dueDate = dueDate;
    }

    public void changeStatus(Status next, String resolution, Instant now) {
        ensureOpen();
        if (next == Status.RESOLVED && (resolution == null || resolution.isBlank())) {
            throw ApiException.badRequest("RESOLUTION_REQUIRED", "Descreva a solução para encerrar a ocorrência.");
        }
        this.status = next;
        if (next.isClosed()) {
            this.resolution = resolution;
            this.resolvedAt = now;
        }
    }

    /** Reabre (ex.: o morador informa que o problema persiste). */
    public void reopen() {
        if (!status.isClosed()) {
            throw ApiException.businessRule("OCCURRENCE_NOT_CLOSED", "A ocorrência não está encerrada.");
        }
        status = Status.OPEN;
        resolvedAt = null;
    }

    public boolean isOverdue(LocalDate today) {
        return !status.isClosed() && dueDate != null && dueDate.isBefore(today);
    }

    private void ensureOpen() {
        if (status.isClosed()) {
            throw ApiException.businessRule("OCCURRENCE_CLOSED", "A ocorrência está encerrada. Reabra-a para alterar.");
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Priority getPriority() {
        return priority;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getReporterUserId() {
        return reporterUserId;
    }

    public String getReporterName() {
        return reporterName;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getResolution() {
        return resolution;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
