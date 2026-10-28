package br.com.tekcondo.billing.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Registro de contato de cobrança com a unidade (histórico de cobrança). Somente inclusão. */
@Entity
@Table(name = "collection_note")
public class CollectionNote {

    public enum ContactType {
        PHONE, EMAIL, WHATSAPP, LETTER, IN_PERSON, LEGAL, OTHER
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "entry_id", updatable = false)
    private UUID entryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, updatable = false)
    private ContactType contactType;

    @Column(name = "contact_date", nullable = false, updatable = false)
    private LocalDate contactDate;

    @Column(name = "note", nullable = false, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    protected CollectionNote() {
    }

    public CollectionNote(UUID organizationId, UUID condominiumId, UUID unitId, UUID entryId, ContactType contactType,
                          LocalDate contactDate, String note, UUID createdBy, String createdByName) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.unitId = unitId;
        this.entryId = entryId;
        this.contactType = contactType;
        this.contactDate = contactDate;
        this.note = note;
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

    public UUID getEntryId() {
        return entryId;
    }

    public ContactType getContactType() {
        return contactType;
    }

    public LocalDate getContactDate() {
        return contactDate;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
