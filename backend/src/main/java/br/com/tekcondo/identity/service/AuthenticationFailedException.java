package br.com.tekcondo.identity.service;

import br.com.tekcondo.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Falha de autenticação. Declarada em {@code noRollbackFor} para que contadores de tentativas e a auditoria
 * da falha sejam efetivados mesmo com a exceção.
 */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "E-mail ou senha inválidos.");
    }

    public static AuthenticationFailedException locked() {
        return new AuthenticationFailedException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Conta temporariamente bloqueada por excesso de tentativas. Tente novamente mais tarde.");
    }

    public static AuthenticationFailedException sessionExpired() {
        return new AuthenticationFailedException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                "Sessão expirada. Faça login novamente.");
    }
}
