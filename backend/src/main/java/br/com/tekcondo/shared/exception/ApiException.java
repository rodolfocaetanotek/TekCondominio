package br.com.tekcondo.shared.exception;

import org.springframework.http.HttpStatus;

/** Exceção de negócio com código estável (para o cliente) e mensagem em pt-BR (para o usuário). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String entityLabel) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", entityLabel + " não encontrado(a).");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException businessRule(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException staleVersion() {
        return conflict("STALE_VERSION",
                "Este registro foi alterado por outro usuário. Recarregue os dados e tente novamente.");
    }
}
