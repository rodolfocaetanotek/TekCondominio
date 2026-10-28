package br.com.tekcondo.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Reserva de área comum por uma unidade. */
@Entity
@Table(name = "reservation")
public class Reservation extends AuditableEntity {

    public enum Status {
        PENDING, CONFIRMED, REJECTED, CANCELLED;

        public boolean isActive() {
            return this == PENDING || this == CONFIRMED;
        }
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "area_id", nullable = false, updatable = false)
    private UUID areaId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "reservation_date", nullable = false, updatable = false)
    private LocalDate date;

    @Convert(converter = MinuteOfDay.class)
    @Column(name = "start_minute", nullable = false, updatable = false)
    private LocalTime startTime;

    @Convert(converter = MinuteOfDay.class)
    @Column(name = "end_minute", nullable = false, updatable = false)
    private LocalTime endTime;

    private Integer guests;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private BigDecimal fee;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "requester_name", nullable = false, updatable = false)
    private String requesterName;

    @Column(name = "by_resident", nullable = false, updatable = false)
    private boolean byResident;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Reservation() {
    }

    public Reservation(CommonArea area, UUID unitId, LocalDate date, LocalTime startTime, LocalTime endTime, Integer guests,
                       String notes, UUID requestedBy, String requesterName, boolean byResident) {
        this.organizationId = area.getOrganizationId();
        this.condominiumId = area.getCondominiumId();
        this.areaId = area.getId();
        this.unitId = unitId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.guests = guests;
        this.notes = notes;
        this.fee = area.getFee();
        this.requestedBy = requestedBy;
        this.requesterName = requesterName;
        this.byResident = byResident;
        this.status = area.isRequiresApproval() && byResident ? Status.PENDING : Status.CONFIRMED;
    }

    public void decide(boolean approved, String note, UUID by, Instant now) {
        if (status != Status.PENDING) {
            throw ApiException.businessRule("RESERVATION_NOT_PENDING", "A reserva não está aguardando aprovação.");
        }
        status = approved ? Status.CONFIRMED : Status.REJECTED;
        decisionNote = note;
        decidedBy = by;
        decidedAt = now;
    }

    public void cancel(String reason, Instant now) {
        if (!status.isActive()) {
            throw ApiException.businessRule("RESERVATION_NOT_ACTIVE", "A reserva já está cancelada ou foi recusada.");
        }
        status = Status.CANCELLED;
        cancelReason = reason;
        cancelledAt = now;
    }

    public void linkCharge(UUID entryId) {
        this.entryId = entryId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getGuests() {
        return guests;
    }

    public String getNotes() {
        return notes;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public boolean isByResident() {
        return byResident;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
