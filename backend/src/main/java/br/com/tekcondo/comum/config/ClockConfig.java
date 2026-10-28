package br.com.tekcondo.comum.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * Relógio injetável: permite testar regras dependentes de data (bloqueios, vencimentos). Usa o fuso dos
     * condomínios, para que {@code LocalDate.now(clock)} seja o "hoje" do usuário — em UTC, entre 21h e 0h
     * (horário de Brasília), seria o dia seguinte. Instantes ({@code Instant.now(clock)}) não mudam com o fuso.
     */
    @Bean
    public Clock clock(@Value("${tekcondo.app.time-zone:America/Sao_Paulo}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
