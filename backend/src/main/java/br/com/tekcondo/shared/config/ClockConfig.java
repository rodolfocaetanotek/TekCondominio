package br.com.tekcondo.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Relógio injetável: permite testar regras dependentes de data (bloqueios, vencimentos). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
