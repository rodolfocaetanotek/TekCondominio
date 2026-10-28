package br.com.tekcondo.administradora.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.administradora.entity.Administradora;

public record AdministradoraResponse(UUID id, String nome, String cnpj, String email, String telefone, boolean ativo,
                                     Instant criadoEm) {

    public static AdministradoraResponse de(Administradora a) {
        return new AdministradoraResponse(a.getId(), a.getNome(), a.getCnpj(), a.getEmail(), a.getTelefone(), a.isAtivo(),
                a.getCriadoEm());
    }
}
