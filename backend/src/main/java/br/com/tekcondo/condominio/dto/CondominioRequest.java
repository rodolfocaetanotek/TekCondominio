package br.com.tekcondo.condominio.dto;

import br.com.tekcondo.condominio.entity.TipoCondominio;
import br.com.tekcondo.comum.validation.Cnpj;
import br.com.tekcondo.comum.validation.Telefone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados cadastrais do condomínio. {@code versao} é obrigatório na atualização (bloqueio otimista)
 * e ignorado na criação.
 */
public record CondominioRequest(
        @NotBlank(message = "Informe o nome do condomínio.") @Size(max = 150, message = "Máximo de 150 caracteres.") String nome,
        @Size(max = 200, message = "Máximo de 200 caracteres.") String razaoSocial,
        @Cnpj String cnpj,
        @NotNull(message = "Informe o tipo do condomínio.") TipoCondominio tipo,
        @Valid EnderecoDto endereco,
        @Email(message = "E-mail inválido.") @Size(max = 254) String email,
        @Telefone String telefone,
        Long versao) {
}
