package br.com.tekcondo.condominio.dto;

import br.com.tekcondo.comum.validation.Uf;
import br.com.tekcondo.comum.validation.Cep;
import jakarta.validation.constraints.Size;

public record EnderecoDto(
        @Cep String cep,
        @Size(max = 200) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @Size(max = 100) String cidade,
        @Uf String uf) {
}
