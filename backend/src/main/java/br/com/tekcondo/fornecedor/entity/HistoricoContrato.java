package br.com.tekcondo.fornecedor.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "contract_history")
public class HistoricoContrato {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID idContrato;

    @Column(name = "change", nullable = false, updatable = false)
    private String alteracao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by_name", updatable = false)
    private String nomeCriadoPor;

    protected HistoricoContrato() {
    }

    public HistoricoContrato(UUID idContrato, String alteracao, String nomeCriadoPor) {
        this.idContrato = idContrato;
        this.alteracao = alteracao.length() > 1000 ? alteracao.substring(0, 1000) : alteracao;
        this.nomeCriadoPor = nomeCriadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public String getAlteracao() {
        return alteracao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getNomeCriadoPor() {
        return nomeCriadoPor;
    }
}
