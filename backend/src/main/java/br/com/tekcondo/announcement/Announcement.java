package br.com.tekcondo.announcement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Comunicado do condomínio, para todos, um bloco ou uma unidade. */
@Entity
@Table(name = "announcement")
public class Announcement {

    public enum Audience {
        ALL, BUILDING, UNIT
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, updatable = false)
    private Audience audience;

    @Column(name = "building_id", updatable = false)
    private UUID buildingId;

    @Column(name = "unit_id", updatable = false)
    private UUID unitId;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "send_email", nullable = false, updatable = false)
    private boolean sendEmail;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    protected Announcement() {
    }

    public Announcement(UUID organizationId, UUID condominiumId, String title, String body, Audience audience,
                        UUID buildingId, UUID unitId, boolean pinned, boolean sendEmail, LocalDate expiresOn,
                        Instant publishedAt, UUID createdBy, String createdByName) {
        if (audience == Audience.BUILDING && buildingId == null || audience == Audience.UNIT && unitId == null) {
            throw ApiException.badRequest("AUDIENCE_TARGET_REQUIRED", "Informe o bloco ou a unidade de destino.");
        }
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.title = title;
        this.body = body;
        this.audience = audience;
        this.buildingId = audience == Audience.BUILDING ? buildingId : null;
        this.unitId = audience == Audience.UNIT ? unitId : null;
        this.pinned = pinned;
        this.sendEmail = sendEmail;
        this.expiresOn = expiresOn;
        this.publishedAt = publishedAt;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
    }

    public void archive(Instant now) {
        if (archivedAt != null) {
            throw ApiException.businessRule("ANNOUNCEMENT_ARCHIVED", "O comunicado já está arquivado.");
        }
        archivedAt = now;
        pinned = false;
    }

    public boolean isVisible(LocalDate today) {
        return archivedAt == null && (expiresOn == null || !expiresOn.isBefore(today));
    }

    public UUID getId() {
        return id;
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

    public String getBody() {
        return body;
    }

    public Audience getAudience() {
        return audience;
    }

    public UUID getBuildingId() {
        return buildingId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public boolean isSendEmail() {
        return sendEmail;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
