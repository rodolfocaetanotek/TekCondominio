package br.com.tekcondo.identity.access;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Permissões efetivas do usuário: as válidas para toda a organização e as restritas a cada condomínio. */
public final class UserGrants {

    private final Set<Permission> organizationWide = EnumSet.noneOf(Permission.class);
    private final Map<UUID, Set<Permission>> byCondominium = new HashMap<>();

    public UserGrants(Collection<PermissionGrant> grants) {
        for (PermissionGrant grant : grants) {
            if (grant.condominiumId() == null) {
                organizationWide.add(grant.permission());
            } else {
                byCondominium.computeIfAbsent(grant.condominiumId(), k -> EnumSet.noneOf(Permission.class))
                        .add(grant.permission());
            }
        }
    }

    public boolean hasOrganizationWide(Permission permission) {
        return organizationWide.contains(permission);
    }

    public boolean has(Permission permission, UUID condominiumId) {
        if (organizationWide.contains(permission)) {
            return true;
        }
        return condominiumId != null && byCondominium.getOrDefault(condominiumId, Set.of()).contains(permission);
    }

    public AccessScope scope(Permission permission) {
        if (organizationWide.contains(permission)) {
            return new AccessScope(true, Set.of());
        }
        Set<UUID> ids = byCondominium.entrySet().stream()
                .filter(e -> e.getValue().contains(permission))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        return new AccessScope(false, ids);
    }

    public Set<Permission> organizationWide() {
        return Set.copyOf(organizationWide);
    }

    public Map<UUID, Set<Permission>> byCondominium() {
        return Map.copyOf(byCondominium);
    }
}
