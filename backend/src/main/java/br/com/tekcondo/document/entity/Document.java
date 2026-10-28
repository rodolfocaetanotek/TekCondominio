package br.com.tekcondo.document.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Documento/anexo. Arquivamento lógico; o arquivo físico é preservado para histórico. */
@Entity
@Table(name = "document")
public class Document {

    public enum Category {
        CONVENTION, INTERNAL_RULES, MINUTES, CONTRACT, INVOICE, RECEIPT, FINANCIAL_STATEMENT, UNIT, OCCURRENCE, OTHER
    }

    public enum Visibility {
        /** Apenas equipe autorizada (administradora, síndico, conselho). */
        INTERNAL,
        /** Também disponível aos moradores no portal. */
        RESIDENTS
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    protected Document() {
    }

    public Document(UUID organizationId, UUID condominiumId, Category category, String title, String description,
                    String fileName, String contentType, long sizeBytes, String sha256, String storageKey,
                    Visibility visibility, String entityType, UUID entityId, UUID unitId, UUID createdBy, String createdByName) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.category = category;
        this.title = title;
        this.description = description;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.visibility = visibility;
        this.entityType = entityType;
        this.entityId = entityId;
        this.unitId = unitId;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void archive(UUID by, Instant now) {
        if (archivedAt != null) {
            throw ApiException.businessRule("DOCUMENT_ARCHIVED", "O documento já está arquivado.");
        }
        archivedAt = now;
        archivedBy = by;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public Category getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
