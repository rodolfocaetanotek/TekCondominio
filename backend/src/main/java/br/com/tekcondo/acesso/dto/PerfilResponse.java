package br.com.tekcondo.acesso.dto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.acesso.autorizacao.Permissao;

/**
 * Perfil do usuário autenticado. As permissões servem apenas para a interface decidir o que exibir;
 * toda autorização é revalidada no backend.
 */
public record PerfilResponse(UUID id, String nome, String email, boolean administradorPlataforma, UUID idAdministradora,
                             String nomeAdministradora, Set<Permissao> permissoesAdministradora,
                             Map<UUID, Set<Permissao>> permissoesCondominio) {
}
