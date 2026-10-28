package br.com.tekcondo.acesso.autorizacao;

import java.util.Set;
import java.util.UUID;

/**
 * Alcance de uma permissão para o usuário: a organização inteira ou um conjunto de condomínios.
 * Usado para filtrar listagens no banco.
 */
public record EscopoAcesso(boolean administradoraInteira, Set<UUID> idsCondominio) {

    public boolean isVazio() {
        return !administradoraInteira && idsCondominio.isEmpty();
    }
}
