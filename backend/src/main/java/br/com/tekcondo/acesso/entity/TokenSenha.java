package br.com.tekcondo.acesso.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Token de uso único para definir (convite) ou redefinir senha. Persistido somente como hash. */
@Entity
@Table(name = "password_token")
public class TokenSenha {

    public enum Finalidade {
        INVITE,
        RESET
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID idUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private Finalidade finalidade;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiraEm;

    @Column(name = "used_at")
    private Instant usadoEm;

    @Column(name = "created_at", nullable = false)
    private Instant criadoEm;

    protected TokenSenha() {
    }

    public TokenSenha(UUID idUsuario, Finalidade finalidade, String tokenHash, Instant criadoEm, Instant expiraEm) {
        this.idUsuario = idUsuario;
        this.finalidade = finalidade;
        this.tokenHash = tokenHash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public boolean isUtilizavel(Instant agora) {
        return usadoEm == null && expiraEm.isAfter(agora);
    }

    public void marcarUsado(Instant agora) {
        usadoEm = agora;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public Finalidade getFinalidade() {
        return finalidade;
    }
}
