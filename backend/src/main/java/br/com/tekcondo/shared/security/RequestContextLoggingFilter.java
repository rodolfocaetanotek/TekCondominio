package br.com.tekcondo.shared.security;

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

    private final CurrentUserProvider currentUserProvider;

    public RequestContextLoggingFilter(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || !requestId.matches("[A-Za-z0-9-]{8,64}")) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        currentUserProvider.find().ifPresent(user -> {
            MDC.put("userId", user.userId().toString());
            if (user.organizationId() != null) {
                MDC.put("organizationId", user.organizationId().toString());
            }
        });
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
