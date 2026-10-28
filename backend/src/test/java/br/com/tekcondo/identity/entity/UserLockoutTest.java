package br.com.tekcondo.identity.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserLockoutTest {

    private final Instant now = Instant.parse("2026-10-27T12:00:00Z");

    @Test
    void locksAfterMaxAttemptsAndUnlocksAfterDuration() {
        User user = User.organizationMember(UUID.randomUUID(), "Ana", "ana@x.com", "hash");

        assertThat(user.registerFailedLogin(3, Duration.ofMinutes(15), now)).isFalse();
        assertThat(user.registerFailedLogin(3, Duration.ofMinutes(15), now)).isFalse();
        assertThat(user.registerFailedLogin(3, Duration.ofMinutes(15), now)).isTrue();

        assertThat(user.isLocked(now.plusSeconds(60))).isTrue();
        assertThat(user.isLocked(now.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void successfulLoginResetsCounter() {
        User user = User.organizationMember(UUID.randomUUID(), "Ana", "ANA@X.com ", "hash");
        user.registerFailedLogin(3, Duration.ofMinutes(15), now);
        user.registerSuccessfulLogin(now);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(now);
        assertThat(user.getEmail()).isEqualTo("ana@x.com");
    }
}
