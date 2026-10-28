package br.com.tekcondo.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Atribuição de papel. {@code condominiumId} nulo = vale para toda a organização. */
@Entity
@Table(name = "user_role")
public class UserRole {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "condominium_id")
    private UUID condominiumId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected UserRole() {
    }

    public UserRole(UUID userId, Role role, UUID organizationId, UUID condominiumId, UUID createdBy) {
        this.userId = userId;
        this.role = role;
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
