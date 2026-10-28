package br.com.tekcondo.acesso.autorizacao;

import java.util.UUID;

/** Linha de concessão: condomínio (nulo = organização inteira) + permissão. */
public record ConcessaoPermissao(UUID idCondominio, Permissao permissao) {
}
