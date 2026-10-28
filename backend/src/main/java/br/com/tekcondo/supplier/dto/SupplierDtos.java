package br.com.tekcondo.supplier.dto;

import java.util.UUID;

import br.com.tekcondo.shared.validation.CpfCnpj;
import br.com.tekcondo.shared.validation.Phone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record SupplierRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150) String name,
            @CpfCnpj String document,
            @Size(max = 80) String category,
            @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @Phone String phone,
            @Size(max = 120) String contactName,
            @Size(max = 500) String notes) {
    }

    public record SupplierResponse(UUID id, String name, String document, String category, String email, String phone,
                                   String contactName, String notes, boolean active, Long version) {
    }
}
