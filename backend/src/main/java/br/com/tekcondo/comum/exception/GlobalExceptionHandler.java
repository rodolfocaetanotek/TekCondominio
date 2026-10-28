package br.com.tekcondo.comum.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Tratamento centralizado: toda falha vira application/problem+json (RFC 9457) com mensagem em pt-BR. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErroCampo(String campo, String mensagem) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> tratarApi(ApiException ex, HttpServletRequest req) {
        return montar(ex.getStatus(), ex.getCodigo(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> tratarValidacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErroCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErroCampo(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return montar(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Há campos inválidos. Verifique os dados informados.",
                req, Map.of("erros", erros));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> tratarRestricao(ConstraintViolationException ex, HttpServletRequest req) {
        List<ErroCampo> erros = ex.getConstraintViolations().stream()
                .map(v -> new ErroCampo(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return montar(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Há parâmetros inválidos.", req, Map.of("erros", erros));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ProblemDetail> tratarRequisicaoMalformada(Exception ex, HttpServletRequest req) {
        return montar(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Requisição malformada ou com tipos inválidos.", req, null);
    }

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> tratarParteAusente(Exception ex, HttpServletRequest req) {
        return montar(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Parâmetro ou arquivo obrigatório não enviado.", req, null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> tratarArquivoGrande(Exception ex, HttpServletRequest req) {
        return montar(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Arquivo maior que o limite permitido.", req, null);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ProblemDetail> tratarCookieAusente(MissingRequestCookieException ex, HttpServletRequest req) {
        return montar(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Sessão expirada. Faça login novamente.", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> tratarAcessoNegado(AccessDeniedException ex, HttpServletRequest req) {
        return montar(HttpStatus.FORBIDDEN, "FORBIDDEN", "Você não tem permissão para realizar esta operação.", req, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> tratarBloqueioOtimista(OptimisticLockingFailureException ex, HttpServletRequest req) {
        ApiException obsoleta = ApiException.versaoObsoleta();
        return montar(obsoleta.getStatus(), obsoleta.getCodigo(), obsoleta.getMessage(), req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> tratarIntegridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade em {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return montar(HttpStatus.CONFLICT, "DATA_INTEGRITY",
                "A operação viola uma regra de integridade (registro duplicado ou vinculado).", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> tratarMetodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return montar(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Método HTTP não suportado.", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> tratarRecursoInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return montar(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado.", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> tratarInesperado(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return montar(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocorreu um erro inesperado. Tente novamente ou contate o suporte.", req, null);
    }

    public static ProblemDetail problema(HttpStatus status, String codigo, String detalhe, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setType(URI.create("https://tekcondo.app/problems/" + codigo.toLowerCase().replace('_', '-')));
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("codigo", codigo);
        if (path != null) {
            pd.setInstance(URI.create(path));
        }
        return pd;
    }

    private ResponseEntity<ProblemDetail> montar(HttpStatus status, String codigo, String detalhe, HttpServletRequest req,
                                                Map<String, Object> extras) {
        ProblemDetail pd = problema(status, codigo, detalhe, req.getRequestURI());
        if (extras != null) {
            extras.forEach(pd::setProperty);
        }
        return ResponseEntity.status(status).body(pd);
    }
}
