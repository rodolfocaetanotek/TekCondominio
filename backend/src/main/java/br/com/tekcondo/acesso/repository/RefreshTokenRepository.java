package br.com.tekcondo.acesso.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.acesso.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revogadoEm = :agora where t.idUsuario = :idUsuario and t.revogadoEm is null")
    int revogarTodosDoUsuario(UUID idUsuario, Instant agora);

    @Modifying
    @Query("update RefreshToken t set t.revogadoEm = :agora where t.idUsuario = :idUsuario and t.revogadoEm is null and t.tokenHash <> :hashMantido")
    int revogarTodosDoUsuarioExceto(UUID idUsuario, String hashMantido, Instant agora);
}
