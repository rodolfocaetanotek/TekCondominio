package br.com.tekcondo.condominium.dto;

import br.com.tekcondo.comum.validation.Uf;
import br.com.tekcondo.comum.validation.Cep;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @Cep String zipCode,
        @Size(max = 200) String street,
        @Size(max = 20) String number,
        @Size(max = 100) String complement,
        @Size(max = 100) String district,
        @Size(max = 100) String city,
        @Uf String state) {
}
