package br.com.tekcondo.acesso.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.acesso.entity.EscopoPapel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs da gestão de usuários e papéis. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record Atribuicao(UUID id, String codigoPapel, String nomePapel, EscopoPapel escopo, UUID idCondominio,
                             String nomeCondominio) {
    }

    public record Resumo(UUID id, String nome, String email, boolean ativo, boolean convitePendente,
                         Instant ultimoLoginEm, List<String> papeis) {
    }

    public record Detalhe(UUID id, String nome, String email, boolean ativo, boolean convitePendente,
                          Instant ultimoLoginEm, Instant criadoEm, Long versao, List<Atribuicao> atribuicoes) {
    }

    public record AtribuicaoRequest(
            @NotBlank(message = "Informe o papel.") @Size(max = 40) String codigoPapel,
            UUID idCondominio) {
    }

    public record CadastroRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Máximo de 150 caracteres.") String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @NotNull(message = "Informe o papel inicial.") @Valid AtribuicaoRequest atribuicao) {
    }

    public record AtualizacaoRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Máximo de 150 caracteres.") String nome,
            @NotNull(message = "Informe a versão do registro.") Long versao) {
    }

    public record PapelResponse(String codigo, String nome, String descricao, EscopoPapel escopo, Set<Permissao> permissoes) {
    }
}
