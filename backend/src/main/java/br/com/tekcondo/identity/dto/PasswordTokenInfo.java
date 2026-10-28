package br.com.tekcondo.identity.dto;

import br.com.tekcondo.identity.entity.PasswordToken;

/** Dados exibidos na tela "definir senha" para o titular do link. */
public record PasswordTokenInfo(PasswordToken.Purpose purpose, String name, String email) {
}
