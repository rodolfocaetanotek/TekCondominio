package br.com.tekcondo.identity.access;

import java.util.Set;
import java.util.UUID;

/**
 * Alcance de uma permissão para o usuário: a organização inteira ou um conjunto de condomínios.
 * Usado para filtrar listagens no banco.
 */
public record AccessScope(boolean organizationWide, Set<UUID> condominiumIds) {

    public boolean isEmpty() {
        return !organizationWide && condominiumIds.isEmpty();
    }
}
