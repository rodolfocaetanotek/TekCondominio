package br.com.tekcondo.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(UUID userId, Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null and t.tokenHash <> :keepHash")
    int revokeAllForUserExcept(UUID userId, String keepHash, Instant now);
}
