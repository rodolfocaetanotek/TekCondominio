package br.com.tekcondo.notificacao;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Notificação dentro do sistema (sino da barra superior). */
@Entity
@Table(name = "notification")
public class Notificacao {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID idUsuario;

    @Column(name = "title", nullable = false, updatable = false)
    private String titulo;

    @Column(name = "body", updatable = false)
    private String texto;

    @Column(name = "link", updatable = false)
    private String link;

    @Column(name = "dedup_key", updatable = false)
    private String chaveDeduplicacao;

    @Column(name = "read_at")
    private Instant lidaEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Notificacao() {
    }

    public Notificacao(UUID idAdministradora, UUID idUsuario, String titulo, String texto, String link, String chaveDeduplicacao) {
        this.idAdministradora = idAdministradora;
        this.idUsuario = idUsuario;
        this.titulo = titulo.length() > 150 ? titulo.substring(0, 150) : titulo;
        this.texto = texto != null && texto.length() > 1000 ? texto.substring(0, 1000) : texto;
        this.link = link;
        this.chaveDeduplicacao = chaveDeduplicacao;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public void marcarLida(Instant agora) {
        if (lidaEm == null) {
            lidaEm = agora;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getTexto() {
        return texto;
    }

    public String getLink() {
        return link;
    }

    public Instant getLidaEm() {
        return lidaEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
