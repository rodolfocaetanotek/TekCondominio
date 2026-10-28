package br.com.tekcondo.condominium.dto;

import br.com.tekcondo.shared.validation.BrazilianState;
import br.com.tekcondo.shared.validation.ZipCode;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @ZipCode String zipCode,
        @Size(max = 200) String street,
        @Size(max = 20) String number,
        @Size(max = 100) String complement,
        @Size(max = 100) String district,
        @Size(max = 100) String city,
        @BrazilianState String state) {
}
