package br.com.tekcondo.condominio.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.condominio.entity.TipoCondominio;

public record CondominioResponse(
        UUID id,
        String nome,
        String razaoSocial,
        String cnpj,
        TipoCondominio tipo,
        EnderecoDto endereco,
        String email,
        String telefone,
        SituacaoCondominio situacao,
        Instant inativadoEm,
        String motivoInativacao,
        Instant criadoEm,
        Instant atualizadoEm,
        Long versao) {
}
