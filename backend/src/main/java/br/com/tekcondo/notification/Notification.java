package br.com.tekcondo.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Notificação dentro do sistema (sino da barra superior). */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "body", updatable = false)
    private String body;

    @Column(name = "link", updatable = false)
    private String link;

    @Column(name = "dedup_key", updatable = false)
    private String dedupKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID organizationId, UUID userId, String title, String body, String link, String dedupKey) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.title = title.length() > 150 ? title.substring(0, 150) : title;
        this.body = body != null && body.length() > 1000 ? body.substring(0, 1000) : body;
        this.link = link;
        this.dedupKey = dedupKey;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
