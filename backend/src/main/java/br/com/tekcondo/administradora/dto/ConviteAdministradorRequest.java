package br.com.tekcondo.administradora.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConviteAdministradorRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 150) String nome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email) {
}
