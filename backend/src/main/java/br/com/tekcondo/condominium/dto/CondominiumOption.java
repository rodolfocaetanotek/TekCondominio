package br.com.tekcondo.condominium.dto;

import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.identity.access.Permission;

/** Opção do seletor de condomínio, com as permissões efetivas do usuário nele (apenas para a interface). */
public record CondominiumOption(UUID id, String name, CondominiumStatus status, Set<Permission> permissions) {
}
