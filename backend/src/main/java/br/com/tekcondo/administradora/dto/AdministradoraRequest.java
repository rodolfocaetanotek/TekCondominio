package br.com.tekcondo.administradora.dto;

import br.com.tekcondo.comum.validation.Cnpj;
import br.com.tekcondo.comum.validation.Telefone;
import br.com.tekcondo.comum.validation.SenhaForte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro de administradora junto com seu primeiro usuário administrador. */
public record AdministradoraRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 150) String nome,
        @NotBlank(message = "Informe o CNPJ.") @Cnpj String cnpj,
        @Email(message = "E-mail inválido.") @Size(max = 254) String email,
        @Telefone String telefone,
        @NotNull(message = "Informe o administrador.") @Valid Administrador administrador) {

    public record Administrador(
            @NotBlank(message = "Informe o nome do administrador.") @Size(max = 150) String nome,
            @NotBlank(message = "Informe o e-mail do administrador.") @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @NotBlank(message = "Informe a senha inicial.") @SenhaForte String senha) {

        @Override
        public String toString() {
            return "Administrador[nome=" + nome + ", email=" + email + ", senha=***]";
        }
    }
}
