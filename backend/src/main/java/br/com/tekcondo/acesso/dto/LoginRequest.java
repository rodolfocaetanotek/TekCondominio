package br.com.tekcondo.acesso.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email,
        @NotBlank(message = "Informe a senha.") @Size(max = 200) String senha) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", senha=***]";
    }
}
