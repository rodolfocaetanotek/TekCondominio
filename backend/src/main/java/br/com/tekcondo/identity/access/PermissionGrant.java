package br.com.tekcondo.identity.access;

import java.util.UUID;

/** Linha de concessão: condomínio (nulo = organização inteira) + permissão. */
public record PermissionGrant(UUID condominiumId, Permission permission) {
}
