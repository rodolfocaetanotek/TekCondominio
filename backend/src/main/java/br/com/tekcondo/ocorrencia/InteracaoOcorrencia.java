package br.com.tekcondo.ocorrencia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Mensagem/andamento da ocorrência. Interações internas não aparecem para o morador. */
@Entity
@Table(name = "occurrence_interaction")
public class InteracaoOcorrencia {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "occurrence_id", nullable = false, updatable = false)
    private UUID idOcorrencia;

    @Column(name = "author_user_id", updatable = false)
    private UUID idUsuarioAutor;

    @Column(name = "author_name", nullable = false, updatable = false)
    private String nomeAutor;

    @Column(name = "message", nullable = false, updatable = false)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_change", updatable = false)
    private Ocorrencia.Situacao situacaoAlterada;

    @Column(name = "internal", nullable = false, updatable = false)
    private boolean interna;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    protected InteracaoOcorrencia() {
    }

    public InteracaoOcorrencia(UUID idOcorrencia, UUID idUsuarioAutor, String nomeAutor, String mensagem,
                                 Ocorrencia.Situacao situacaoAlterada, boolean interna) {
        this.idOcorrencia = idOcorrencia;
        this.idUsuarioAutor = idUsuarioAutor;
        this.nomeAutor = nomeAutor;
        this.mensagem = mensagem;
        this.situacaoAlterada = situacaoAlterada;
        this.interna = interna;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNomeAutor() {
        return nomeAutor;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Ocorrencia.Situacao getSituacaoAlterada() {
        return situacaoAlterada;
    }

    public boolean isInterna() {
        return interna;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
