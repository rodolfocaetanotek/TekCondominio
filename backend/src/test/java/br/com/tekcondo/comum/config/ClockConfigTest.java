package br.com.tekcondo.comum.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void dataDeNegocioSegueFusoDoCondominio() {
        Clock clock = new ClockConfig().clock("America/Sao_Paulo");
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));

        // 22h30 em Brasília = 01h30 UTC do dia seguinte: o "hoje" de negócio continua sendo o dia em Brasília.
        Clock lateEvening = Clock.fixed(Instant.parse("2026-10-29T01:30:00Z"), clock.getZone());
        assertThat(LocalDate.now(lateEvening)).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(LocalDate.now(lateEvening.withZone(ZoneId.of("UTC")))).isEqualTo(LocalDate.of(2026, 10, 29));
    }
}
