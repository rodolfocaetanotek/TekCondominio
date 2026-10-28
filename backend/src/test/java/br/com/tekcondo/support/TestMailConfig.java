package br.com.tekcondo.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestMailConfig {

    @Bean
    @Primary
    public RecordingMailGateway recordingMailGateway() {
        return new RecordingMailGateway();
    }
}
