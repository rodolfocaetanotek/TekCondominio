package br.com.tekcondo.comum.security;

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
    private static final int MAX_IPS_MONITORADOS = 50_000;

    private record Janela(long inicioMillis, int contagem) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final int maxRequisicoes;
    private final long janelaMillis;
    private final Clock clock;

    public AuthRateLimitFilter(@Value("${tekcondo.security.auth-rate-limit.max-requests:20}") int maxRequisicoes,
                               @Value("${tekcondo.security.auth-rate-limit.window:PT5M}") Duration janela, Clock clock) {
        this.maxRequisicoes = maxRequisicoes;
        this.janelaMillis = janela.toMillis();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(uri.equals("/api/v1/auth/login") || uri.startsWith("/api/v1/auth/senha/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = IpCliente.de(request);
        long agora = clock.millis();
        if (janelas.size() > MAX_IPS_MONITORADOS) {
            janelas.entrySet().removeIf(e -> agora - e.getValue().inicioMillis() >= janelaMillis);
        }
        Janela j = janelas.compute(ip == null ? "?" : ip, (k, anterior) ->
                anterior == null || agora - anterior.inicioMillis() >= janelaMillis ? new Janela(agora, 1) : new Janela(anterior.inicioMillis(), anterior.contagem() + 1));
        if (j.contagem() > maxRequisicoes) {
            long tentarApos = Math.max(1, (j.inicioMillis() + janelaMillis - agora) / 1000);
            if (j.contagem() == maxRequisicoes + 1) {
                log.warn("Limite de tentativas de autenticação excedido para o IP {}", ip);
            }
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(tentarApos));
            response.setContentType("application/problem+json;charset=UTF-8");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"codigo\":\"TOO_MANY_REQUESTS\",\"detail\":\"Muitas tentativas a partir deste endereço. Aguarde "
                    + tentarApos + " segundos e tente novamente.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
