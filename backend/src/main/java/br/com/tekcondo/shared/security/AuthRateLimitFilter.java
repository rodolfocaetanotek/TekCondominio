package br.com.tekcondo.shared.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de tentativas por IP nos endpoints públicos de autenticação (login, recuperação e redefinição de senha),
 * complementando o bloqueio por conta: dificulta ataques de força bruta e de "credential stuffing" distribuídos
 * entre várias contas. Janela fixa em memória — adequado a uma instância; com várias instâncias, aplicar o limite
 * também no proxy reverso (ver docs/SEGURANCA.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final int MAX_TRACKED_IPS = 50_000;

    private record Window(long startMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;

    public AuthRateLimitFilter(@Value("${tekcondo.security.auth-rate-limit.max-requests:20}") int maxRequests,
                               @Value("${tekcondo.security.auth-rate-limit.window:PT5M}") Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(uri.equals("/api/v1/auth/login") || uri.startsWith("/api/v1/auth/password/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientIp.of(request);
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_IPS) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() >= windowMillis);
        }
        Window w = windows.compute(ip == null ? "?" : ip, (k, old) ->
                old == null || now - old.startMillis() >= windowMillis ? new Window(now, 1) : new Window(old.startMillis(), old.count() + 1));
        if (w.count() > maxRequests) {
            long retryAfter = Math.max(1, (w.startMillis() + windowMillis - now) / 1000);
            if (w.count() == maxRequests + 1) {
                log.warn("Limite de tentativas de autenticação excedido para o IP {}", ip);
            }
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/problem+json;charset=UTF-8");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"code\":\"TOO_MANY_REQUESTS\",\"detail\":\"Muitas tentativas a partir deste endereço. Aguarde "
                    + retryAfter + " segundos e tente novamente.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
