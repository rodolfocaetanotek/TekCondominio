package br.com.tekcondo.identity.dto;

import br.com.tekcondo.shared.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpos das requisições de senha. Os toString mascaram senhas e tokens. */
public final class PasswordRequests {

    private PasswordRequests() {
    }

    public record Forgot(@NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email) {
    }

    public record TokenOnly(@NotBlank(message = "Link inválido.") @Size(max = 200) String token) {

        @Override
        public String toString() {
            return "TokenOnly[***]";
        }
    }

    public record Reset(@NotBlank(message = "Link inválido.") @Size(max = 200) String token,
                        @NotBlank(message = "Informe a nova senha.") @StrongPassword String newPassword) {

        @Override
        public String toString() {
            return "Reset[***]";
        }
    }

    public record Change(@NotBlank(message = "Informe a senha atual.") @Size(max = 200) String currentPassword,
                         @NotBlank(message = "Informe a nova senha.") @StrongPassword String newPassword) {

        @Override
        public String toString() {
            return "Change[***]";
        }
    }
}
