package br.com.tekcondo.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestEmailConfig {

    @Bean
    @Primary
    public GravacaoEmailGateway gravacaoEmailGateway() {
        return new GravacaoEmailGateway();
    }
}
