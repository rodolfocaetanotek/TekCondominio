package br.com.tekcondo.financeiro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Histórico de fechamentos e reaberturas do período financeiro. Somente inclusão. */
@Entity
@Table(name = "period_closing")
public class FechamentoPeriodo {

    public enum Acao {
        CLOSE, REOPEN
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private Acao acao;

    @Column(name = "closed_until", updatable = false)
    private LocalDate fechadoAte;

    @Column(name = "previous_until", updatable = false)
    private LocalDate fechadoAteAnterior;

    @Column(name = "notes", updatable = false)
    private String observacoes;

    /** Fotografia dos saldos e totais (JSON gravado; as chaves continuam em inglês por serem dado histórico). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", updatable = false)
    private Map<String, Object> fotografia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "created_by_name", updatable = false)
    private String nomeCriadoPor;

    protected FechamentoPeriodo() {
    }

    public FechamentoPeriodo(UUID idAdministradora, UUID idCondominio, Acao acao, LocalDate fechadoAte,
                             LocalDate fechadoAteAnterior, String observacoes, Map<String, Object> fotografia, UUID criadoPor,
                             String nomeCriadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.acao = acao;
        this.fechadoAte = fechadoAte;
        this.fechadoAteAnterior = fechadoAteAnterior;
        this.observacoes = observacoes;
        this.fotografia = fotografia;
        this.criadoPor = criadoPor;
        this.nomeCriadoPor = nomeCriadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Acao getAcao() {
        return acao;
    }

    public LocalDate getFechadoAte() {
        return fechadoAte;
    }

    public LocalDate getFechadoAteAnterior() {
        return fechadoAteAnterior;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Map<String, Object> getFotografia() {
        return fotografia;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getNomeCriadoPor() {
        return nomeCriadoPor;
    }
}
