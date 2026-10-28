package br.com.tekcondo.occurrence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Mensagem/andamento da ocorrência. Interações internas não aparecem para o morador. */
@Entity
@Table(name = "occurrence_interaction")
public class OccurrenceInteraction {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "occurrence_id", nullable = false, updatable = false)
    private UUID occurrenceId;

    @Column(name = "author_user_id", updatable = false)
    private UUID authorUserId;

    @Column(name = "author_name", nullable = false, updatable = false)
    private String authorName;

    @Column(name = "message", nullable = false, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_change", updatable = false)
    private Occurrence.Status statusChange;

    @Column(name = "internal", nullable = false, updatable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OccurrenceInteraction() {
    }

    public OccurrenceInteraction(UUID occurrenceId, UUID authorUserId, String authorName, String message,
                                 Occurrence.Status statusChange, boolean internal) {
        this.occurrenceId = occurrenceId;
        this.authorUserId = authorUserId;
        this.authorName = authorName;
        this.message = message;
        this.statusChange = statusChange;
        this.internal = internal;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getMessage() {
        return message;
    }

    public Occurrence.Status getStatusChange() {
        return statusChange;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
