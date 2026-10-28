package br.com.tekcondo.comum.exception;

import org.springframework.http.HttpStatus;

/** Exceção de negócio com código estável (para o cliente) e mensagem em pt-BR (para o usuário). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public static ApiException naoEncontrado(String rotuloEntidade) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", rotuloEntidade + " não encontrado(a).");
    }

    public static ApiException conflito(String codigo, String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensagem);
    }

    public static ApiException regraNegocio(String codigo, String mensagem) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem);
    }

    public static ApiException requisicaoInvalida(String codigo, String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, codigo, mensagem);
    }

    public static ApiException versaoObsoleta() {
        return conflito("STALE_VERSION",
                "Este registro foi alterado por outro usuário. Recarregue os dados e tente novamente.");
    }
}
