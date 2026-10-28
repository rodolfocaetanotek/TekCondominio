package br.com.tekcondo.assembly;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Entidades do módulo de assembleias. */
public final class AssemblyEntities {

    private AssemblyEntities() {
    }

    @Entity
    @Table(name = "assembly")
    public static class Assembly extends AuditableEntity {

        public enum Kind {
            ORDINARY, EXTRAORDINARY
        }

        public enum VoteWeight {
            UNIT, IDEAL_FRACTION
        }

        public enum Status {
            DRAFT, CALLED, IN_PROGRESS, CLOSED, CANCELLED
        }

        @Column(name = "organization_id", nullable = false, updatable = false)
        private UUID organizationId;

        @Column(name = "condominium_id", nullable = false, updatable = false)
        private UUID condominiumId;

        @Column(nullable = false)
        private String title;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Kind kind;

        @Column(name = "scheduled_at", nullable = false)
        private Instant scheduledAt;

        @Column(name = "second_call_at")
        private Instant secondCallAt;

        @Column(nullable = false)
        private String location;

        @Enumerated(EnumType.STRING)
        @Column(name = "vote_weight", nullable = false)
        private VoteWeight voteWeight;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Status status = Status.DRAFT;

        private String minutes;

        @Column(name = "called_at")
        private Instant calledAt;

        @Column(name = "closed_at")
        private Instant closedAt;

        @Column(name = "cancel_reason")
        private String cancelReason;

        @Column(name = "created_by", updatable = false)
        private UUID createdBy;

        protected Assembly() {
        }

        public Assembly(UUID organizationId, UUID condominiumId, UUID createdBy) {
            this.organizationId = organizationId;
            this.condominiumId = condominiumId;
            this.createdBy = createdBy;
        }

        public void details(String title, Kind kind, Instant scheduledAt, Instant secondCallAt, String location, VoteWeight weight) {
            if (status != Status.DRAFT && status != Status.CALLED) {
                throw ApiException.businessRule("ASSEMBLY_LOCKED", "A assembleia já foi iniciada e não pode ser alterada.");
            }
            if (secondCallAt != null && secondCallAt.isBefore(scheduledAt)) {
                throw ApiException.businessRule("SECOND_CALL_BEFORE_FIRST", "A segunda convocação deve ser após a primeira.");
            }
            this.title = title;
            this.kind = kind;
            this.scheduledAt = scheduledAt;
            this.secondCallAt = secondCallAt;
            this.location = location;
            this.voteWeight = weight;
        }

        public void call(Instant now) {
            require(Status.DRAFT, "Somente assembleias em rascunho podem ser convocadas.");
            status = Status.CALLED;
            calledAt = now;
        }

        public void start() {
            require(Status.CALLED, "A assembleia precisa estar convocada para ser iniciada.");
            status = Status.IN_PROGRESS;
        }

        public void close(Instant now) {
            require(Status.IN_PROGRESS, "Somente assembleias em andamento podem ser encerradas.");
            status = Status.CLOSED;
            closedAt = now;
        }

        public void cancel(String reason) {
            if (status == Status.CLOSED || status == Status.CANCELLED) {
                throw ApiException.businessRule("ASSEMBLY_FINISHED", "A assembleia já foi encerrada ou cancelada.");
            }
            status = Status.CANCELLED;
            cancelReason = reason;
        }

        public void writeMinutes(String text) {
            if (status != Status.IN_PROGRESS && status != Status.CLOSED) {
                throw ApiException.businessRule("ASSEMBLY_NOT_HELD", "A ata é registrada durante ou após a realização da assembleia.");
            }
            minutes = text;
        }

        public void requireInProgress() {
            require(Status.IN_PROGRESS, "Disponível apenas com a assembleia em andamento.");
        }

        private void require(Status expected, String message) {
            if (status != expected) {
                throw ApiException.businessRule("ASSEMBLY_INVALID_STATUS", message);
            }
        }

        public UUID getOrganizationId() {
            return organizationId;
        }

        public UUID getCondominiumId() {
            return condominiumId;
        }

        public String getTitle() {
            return title;
        }

        public Kind getKind() {
            return kind;
        }

        public Instant getScheduledAt() {
            return scheduledAt;
        }

        public Instant getSecondCallAt() {
            return secondCallAt;
        }

        public String getLocation() {
            return location;
        }

        public VoteWeight getVoteWeight() {
            return voteWeight;
        }

        public Status getStatus() {
            return status;
        }

        public String getMinutes() {
            return minutes;
        }

        public Instant getCalledAt() {
            return calledAt;
        }

        public Instant getClosedAt() {
            return closedAt;
        }

        public String getCancelReason() {
            return cancelReason;
        }
    }

    @Entity
    @Table(name = "assembly_agenda_item")
    public static class AgendaItem {

        @Id
        private UUID id = UUID.randomUUID();

        @Column(name = "assembly_id", nullable = false, updatable = false)
        private UUID assemblyId;

        @Column(nullable = false)
        private int position;

        @Column(nullable = false)
        private String title;

        private String description;

        @Column(nullable = false)
        private boolean voting;

        private String deliberation;

        private String responsible;

        private LocalDate deadline;

        protected AgendaItem() {
        }

        public AgendaItem(UUID assemblyId, int position, String title, String description, boolean voting) {
            this.assemblyId = assemblyId;
            this.position = position;
            this.title = title;
            this.description = description;
            this.voting = voting;
        }

        public void deliberate(String deliberation, String responsible, LocalDate deadline) {
            this.deliberation = deliberation;
            this.responsible = responsible;
            this.deadline = deadline;
        }

        public UUID getId() {
            return id;
        }

        public UUID getAssemblyId() {
            return assemblyId;
        }

        public int getPosition() {
            return position;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public boolean isVoting() {
            return voting;
        }

        public String getDeliberation() {
            return deliberation;
        }

        public String getResponsible() {
            return responsible;
        }

        public LocalDate getDeadline() {
            return deadline;
        }
    }

    @Entity
    @Table(name = "assembly_attendance")
    public static class Attendance {

        @Id
        private UUID id = UUID.randomUUID();

        @Column(name = "assembly_id", nullable = false, updatable = false)
        private UUID assemblyId;

        @Column(name = "unit_id", nullable = false, updatable = false)
        private UUID unitId;

        @Column(name = "attendee_name", nullable = false, updatable = false)
        private String attendeeName;

        @Column(name = "by_proxy", nullable = false, updatable = false)
        private boolean byProxy;

        @Column(name = "proxy_name", updatable = false)
        private String proxyName;

        @Column(name = "registered_at", nullable = false, updatable = false)
        private Instant registeredAt;

        @Column(name = "registered_by", updatable = false)
        private UUID registeredBy;

        protected Attendance() {
        }

        public Attendance(UUID assemblyId, UUID unitId, String attendeeName, boolean byProxy, String proxyName, UUID registeredBy) {
            if (byProxy && (proxyName == null || proxyName.isBlank())) {
                throw ApiException.badRequest("PROXY_NAME_REQUIRED", "Informe o nome do procurador.");
            }
            this.assemblyId = assemblyId;
            this.unitId = unitId;
            this.attendeeName = attendeeName;
            this.byProxy = byProxy;
            this.proxyName = byProxy ? proxyName : null;
            this.registeredBy = registeredBy;
        }

        @PrePersist
        void onCreate() {
            registeredAt = Instant.now();
        }

        public UUID getId() {
            return id;
        }

        public UUID getUnitId() {
            return unitId;
        }

        public UUID getAssemblyId() {
            return assemblyId;
        }

        public String getAttendeeName() {
            return attendeeName;
        }

        public boolean isByProxy() {
            return byProxy;
        }

        public String getProxyName() {
            return proxyName;
        }

        public Instant getRegisteredAt() {
            return registeredAt;
        }
    }

    @Entity
    @Table(name = "assembly_vote")
    public static class Vote {

        public enum Choice {
            YES, NO, ABSTAIN
        }

        @Id
        private UUID id = UUID.randomUUID();

        @Column(name = "assembly_id", nullable = false, updatable = false)
        private UUID assemblyId;

        @Column(name = "agenda_item_id", nullable = false, updatable = false)
        private UUID agendaItemId;

        @Column(name = "unit_id", nullable = false, updatable = false)
        private UUID unitId;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, updatable = false)
        private Choice choice;

        @Column(nullable = false, updatable = false)
        private BigDecimal weight;

        @Column(name = "registered_at", nullable = false, updatable = false)
        private Instant registeredAt;

        @Column(name = "registered_by", updatable = false)
        private UUID registeredBy;

        protected Vote() {
        }

        public Vote(UUID assemblyId, UUID agendaItemId, UUID unitId, Choice choice, BigDecimal weight, UUID registeredBy) {
            this.assemblyId = assemblyId;
            this.agendaItemId = agendaItemId;
            this.unitId = unitId;
            this.choice = choice;
            this.weight = weight;
            this.registeredBy = registeredBy;
        }

        @PrePersist
        void onCreate() {
            registeredAt = Instant.now();
        }

        public UUID getAgendaItemId() {
            return agendaItemId;
        }

        public UUID getUnitId() {
            return unitId;
        }

        public Choice getChoice() {
            return choice;
        }

        public BigDecimal getWeight() {
            return weight;
        }
    }
}
