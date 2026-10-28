package br.com.tekcondo.acesso.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BloqueioUsuarioTest {

    private final Instant agora = Instant.parse("2026-10-27T12:00:00Z");

    @Test
    void bloqueiaAposMaximoDeTentativasELiberaAposDuracao() {
        Usuario usuario = Usuario.membroAdministradora(UUID.randomUUID(), "Ana", "ana@x.com", "hash");

        assertThat(usuario.registrarFalhaLogin(3, Duration.ofMinutes(15), agora)).isFalse();
        assertThat(usuario.registrarFalhaLogin(3, Duration.ofMinutes(15), agora)).isFalse();
        assertThat(usuario.registrarFalhaLogin(3, Duration.ofMinutes(15), agora)).isTrue();

        assertThat(usuario.isBloqueado(agora.plusSeconds(60))).isTrue();
        assertThat(usuario.isBloqueado(agora.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void loginComSucessoZeraContador() {
        Usuario usuario = Usuario.membroAdministradora(UUID.randomUUID(), "Ana", "ANA@X.com ", "hash");
        usuario.registrarFalhaLogin(3, Duration.ofMinutes(15), agora);
        usuario.registrarLoginSucesso(agora);

        assertThat(usuario.getTentativasLoginFalhas()).isZero();
        assertThat(usuario.getUltimoLoginEm()).isEqualTo(agora);
        assertThat(usuario.getEmail()).isEqualTo("ana@x.com");
    }
}
