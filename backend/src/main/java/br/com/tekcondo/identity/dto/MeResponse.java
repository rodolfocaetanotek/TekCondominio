package br.com.tekcondo.identity.dto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.identity.access.Permission;

/**
 * Perfil do usuário autenticado. As permissões servem apenas para a interface decidir o que exibir;
 * toda autorização é revalidada no backend.
 */
public record MeResponse(UUID id, String name, String email, boolean platformAdmin, UUID organizationId,
                         String organizationName, Set<Permission> organizationPermissions,
                         Map<UUID, Set<Permission>> condominiumPermissions) {
}
