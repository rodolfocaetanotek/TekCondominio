package br.com.tekcondo.acesso.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.acesso.entity.TokenSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TokenSenhaRepository extends JpaRepository<TokenSenha, UUID> {

    Optional<TokenSenha> findByTokenHash(String tokenHash);

    long countByIdUsuarioAndFinalidadeAndCriadoEmAfter(UUID idUsuario, TokenSenha.Finalidade finalidade, Instant desde);

    /** Invalida os tokens pendentes do usuário (ex.: ao emitir um novo ou ao concluir a troca de senha). */
    @Modifying
    @Query("update TokenSenha t set t.usadoEm = :agora where t.idUsuario = :idUsuario and t.usadoEm is null")
    int invalidarPendentes(UUID idUsuario, Instant agora);
}
