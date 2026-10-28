package br.com.tekcondo.identity.entity;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Papel = conjunto de permissões. {@code organizationId} nulo indica papel padrão do sistema. */
@Entity
@Table(name = "role")
public class Role extends AuditableEntity {

    public static final String ORG_ADMIN = "ORG_ADMIN";

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleScope scope;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    protected Role() {
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public RoleScope getScope() {
        return scope;
    }

    public Set<Permission> getPermissions() {
        return Set.copyOf(permissions);
    }
}
