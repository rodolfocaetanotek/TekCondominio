package br.com.tekcondo.acesso.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Refresh token persistido somente como hash SHA-256; o valor em claro existe apenas no cookie do cliente. */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID idUsuario;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiraEm;

    @Column(name = "revoked_at")
    private Instant revogadoEm;

    @Column(name = "created_at", nullable = false)
    private Instant criadoEm;

    protected RefreshToken() {
    }

    public RefreshToken(UUID idUsuario, String tokenHash, Instant criadoEm, Instant expiraEm) {
        this.idUsuario = idUsuario;
        this.tokenHash = tokenHash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public boolean isRevogado() {
        return revogadoEm != null;
    }

    public boolean isExpirado(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    public void revogar(Instant agora) {
        if (revogadoEm == null) {
            revogadoEm = agora;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }
}
