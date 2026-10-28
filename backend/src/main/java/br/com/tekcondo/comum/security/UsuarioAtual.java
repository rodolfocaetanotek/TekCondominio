package br.com.tekcondo.comum.security;

import java.util.UUID;

/** Identidade autenticada, extraída do JWT assinado (nunca de parâmetros enviados pelo cliente). */
public record UsuarioAtual(UUID idUsuario, UUID idAdministradora, String email, String nome, boolean administradorPlataforma) {

    public boolean pertenceAAdministradora() {
        return idAdministradora != null;
    }
}
