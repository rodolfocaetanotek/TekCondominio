package br.com.tekcondo.condominio.dto;

import java.util.UUID;

import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.condominio.entity.TipoCondominio;

/** Linha da listagem de condomínios. */
public record CondominioResumo(UUID id, String nome, String cnpj, TipoCondominio tipo, String cidade, String uf,
                               SituacaoCondominio situacao) {
}
