package br.com.tekcondo.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final MutableClock clock = new MutableClock();
    final AuthRateLimitFilter filter = new AuthRateLimitFilter(3, Duration.ofMinutes(5), clock);
    final AtomicInteger passed = new AtomicInteger();

    int post(String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (rq, rs) -> passed.incrementAndGet());
        return res.getStatus();
    }

    @Test
    void blocksAfterLimitPerIpAndResetsAfterWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/v1/auth/login", "10.0.0.1")).isEqualTo(200);
        }
        assertThat(post("/api/v1/auth/login", "10.0.0.1")).isEqualTo(429);
        assertThat(post("/api/v1/auth/password/forgot", "10.0.0.1")).isEqualTo(429); // mesma janela para os endpoints de autenticação
        assertThat(post("/api/v1/auth/login", "10.0.0.2")).isEqualTo(200);           // outro IP não é afetado
        assertThat(post("/api/v1/condominiums", "10.0.0.1")).isEqualTo(200);         // demais rotas não são limitadas aqui

        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThat(post("/api/v1/auth/login", "10.0.0.1")).isEqualTo(200);
        assertThat(passed.get()).isEqualTo(6);
    }
}
