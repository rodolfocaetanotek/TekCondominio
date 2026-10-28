package br.com.tekcondo.condominium.dto;

import br.com.tekcondo.condominium.entity.CondominiumType;
import br.com.tekcondo.shared.validation.Cnpj;
import br.com.tekcondo.shared.validation.Phone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados cadastrais do condomínio. {@code version} é obrigatório na atualização (bloqueio otimista)
 * e ignorado na criação.
 */
public record CondominiumRequest(
        @NotBlank(message = "Informe o nome do condomínio.") @Size(max = 150, message = "Máximo de 150 caracteres.") String name,
        @Size(max = 200, message = "Máximo de 200 caracteres.") String legalName,
        @Cnpj String cnpj,
        @NotNull(message = "Informe o tipo do condomínio.") CondominiumType type,
        @Valid AddressDto address,
        @Email(message = "E-mail inválido.") @Size(max = 254) String email,
        @Phone String phone,
        Long version) {
}
