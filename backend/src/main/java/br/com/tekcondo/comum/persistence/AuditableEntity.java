package br.com.tekcondo.comum.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

/**
 * Base das entidades com identificador UUID gerado na aplicação, carimbo de data e bloqueio otimista.
 * {@code versao == null} indica entidade nova para o Spring Data.
 */
@MappedSuperclass
public abstract class AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "updated_at", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(name = "version")
    private Long versao;

    @PrePersist
    protected void aoCriar() {
        criadoEm = Instant.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    protected void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Long getVersao() {
        return versao;
    }
}
