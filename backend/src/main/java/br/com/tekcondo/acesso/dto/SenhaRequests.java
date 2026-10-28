package br.com.tekcondo.acesso.dto;

import br.com.tekcondo.comum.validation.SenhaForte;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpos das requisições de senha. Os toString mascaram senhas e tokens. */
public final class SenhaRequests {

    private SenhaRequests() {
    }

    public record Esqueci(@NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email) {
    }

    public record SomenteToken(@NotBlank(message = "Link inválido.") @Size(max = 200) String token) {

        @Override
        public String toString() {
            return "SomenteToken[***]";
        }
    }

    public record Redefinicao(@NotBlank(message = "Link inválido.") @Size(max = 200) String token,
                              @NotBlank(message = "Informe a nova senha.") @SenhaForte String novaSenha) {

        @Override
        public String toString() {
            return "Redefinicao[***]";
        }
    }

    public record Alteracao(@NotBlank(message = "Informe a senha atual.") @Size(max = 200) String senhaAtual,
                            @NotBlank(message = "Informe a nova senha.") @SenhaForte String novaSenha) {

        @Override
        public String toString() {
            return "Alteracao[***]";
        }
    }
}
