package br.com.tekcondo.comum.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enriquece os logs estruturados com requestId, userId e organizationId (executa após a autenticação). */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class RequestContextLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final UsuarioAtualProvider usuarioAtualProvider;

    public RequestContextLoggingFilter(UsuarioAtualProvider usuarioAtualProvider) {
        this.usuarioAtualProvider = usuarioAtualProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idRequisicao = request.getHeader(REQUEST_ID_HEADER);
        if (idRequisicao == null || !idRequisicao.matches("[A-Za-z0-9-]{8,64}")) {
            idRequisicao = UUID.randomUUID().toString();
        }
        MDC.put("requestId", idRequisicao);
        response.setHeader(REQUEST_ID_HEADER, idRequisicao);
        usuarioAtualProvider.buscar().ifPresent(usuario -> {
            MDC.put("userId", usuario.idUsuario().toString());
            if (usuario.idAdministradora() != null) {
                MDC.put("organizationId", usuario.idAdministradora().toString());
            }
        });
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
