package br.com.tekcondo.acesso.dto;

import br.com.tekcondo.acesso.entity.TokenSenha;

/** Dados exibidos na tela "definir senha" para o titular do link. */
public record TokenSenhaInfo(TokenSenha.Finalidade finalidade, String nome, String email) {
}
