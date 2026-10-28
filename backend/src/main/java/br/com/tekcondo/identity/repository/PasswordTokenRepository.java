package br.com.tekcondo.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.identity.entity.PasswordToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordTokenRepository extends JpaRepository<PasswordToken, UUID> {

    Optional<PasswordToken> findByTokenHash(String tokenHash);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, PasswordToken.Purpose purpose, Instant since);

    /** Invalida os tokens pendentes do usuário (ex.: ao emitir um novo ou ao concluir a troca de senha). */
    @Modifying
    @Query("update PasswordToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidatePending(UUID userId, Instant now);
}
