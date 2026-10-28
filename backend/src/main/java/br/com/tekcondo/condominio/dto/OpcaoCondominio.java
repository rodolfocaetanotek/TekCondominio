package br.com.tekcondo.condominio.dto;

import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;

/** Opção do seletor de condomínio, com as permissões efetivas do usuário nele (apenas para a interface). */
public record OpcaoCondominio(UUID id, String nome, SituacaoCondominio situacao, Set<Permissao> permissoes) {
}
