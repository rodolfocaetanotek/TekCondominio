package br.com.tekcondo.fornecedor.dto;

import java.util.UUID;

import br.com.tekcondo.comum.validation.CpfCnpj;
import br.com.tekcondo.comum.validation.Telefone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class FornecedorDtos {

    private FornecedorDtos() {
    }

    public record FornecedorRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150) String nome,
            @CpfCnpj String documento,
            @Size(max = 80) String categoria,
            @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @Telefone String telefone,
            @Size(max = 120) String nomeContato,
            @Size(max = 500) String observacoes) {
    }

    public record FornecedorResponse(UUID id, String nome, String documento, String categoria, String email, String telefone,
                                     String nomeContato, String observacoes, boolean ativo, Long versao) {
    }
}
