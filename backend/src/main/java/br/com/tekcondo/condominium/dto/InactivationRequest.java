package br.com.tekcondo.condominium.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InactivationRequest(
        @NotBlank(message = "Informe a justificativa.") @Size(min = 5, max = 500, message = "A justificativa deve ter entre 5 e 500 caracteres.") String reason) {
}
