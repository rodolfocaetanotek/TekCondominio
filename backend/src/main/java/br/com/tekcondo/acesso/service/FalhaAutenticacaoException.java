package br.com.tekcondo.acesso.service;

import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Falha de autenticação. Declarada em {@code noRollbackFor} para que contadores de tentativas e a auditoria
 * da falha sejam efetivados mesmo com a exceção.
 */
public class FalhaAutenticacaoException extends ApiException {

    public FalhaAutenticacaoException(HttpStatus status, String codigo, String mensagem) {
        super(status, codigo, mensagem);
    }

    public static FalhaAutenticacaoException credenciaisInvalidas() {
        return new FalhaAutenticacaoException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "E-mail ou senha inválidos.");
    }

    public static FalhaAutenticacaoException contaBloqueada() {
        return new FalhaAutenticacaoException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Conta temporariamente bloqueada por excesso de tentativas. Tente novamente mais tarde.");
    }

    public static FalhaAutenticacaoException sessaoExpirada() {
        return new FalhaAutenticacaoException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                "Sessão expirada. Faça login novamente.");
    }
}
