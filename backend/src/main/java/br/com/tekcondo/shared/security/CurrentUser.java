package br.com.tekcondo.shared.security;

import java.util.UUID;

/** Identidade autenticada, extraída do JWT assinado (nunca de parâmetros enviados pelo cliente). */
public record CurrentUser(UUID userId, UUID organizationId, String email, String name, boolean platformAdmin) {

    public boolean belongsToOrganization() {
        return organizationId != null;
    }
}
