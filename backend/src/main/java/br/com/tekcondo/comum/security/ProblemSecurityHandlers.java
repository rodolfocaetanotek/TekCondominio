package br.com.tekcondo.comum.security;

import java.io.IOException;

import br.com.tekcondo.comum.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Respostas 401/403 da cadeia de filtros no mesmo formato problem+json da API. */
@Component
public class ProblemSecurityHandlers {

    private final ObjectMapper objectMapper;

    public ProblemSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> escrever(response, GlobalExceptionHandler.problema(HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Autenticação necessária ou sessão expirada.", request.getRequestURI()));
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> escrever(response, GlobalExceptionHandler.problema(HttpStatus.FORBIDDEN,
                "FORBIDDEN", "Você não tem permissão para realizar esta operação.", request.getRequestURI()));
    }

    private void escrever(HttpServletResponse response, ProblemDetail problema) throws IOException {
        response.setStatus(problema.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}
