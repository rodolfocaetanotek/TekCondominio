package br.com.tekcondo.ocorrencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "occurrence")
public class Ocorrencia extends AuditableEntity {

    public enum Categoria {
        MAINTENANCE, NOISE, SECURITY, CLEANING, COMPLAINT, REQUEST, SUGGESTION, OTHER
    }

    public enum Prioridade {
        LOW, MEDIUM, HIGH, URGENT
    }

    public enum Situacao {
        OPEN, IN_PROGRESS, WAITING, RESOLVED, CANCELLED;

        boolean isEncerrada() {
            return this == RESOLVED || this == CANCELLED;
        }
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "number", nullable = false, updatable = false)
    private int numero;

    @Column(name = "title", nullable = false)
    private String titulo;

    @Column(name = "description", nullable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private Prioridade prioridade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao = Situacao.OPEN;

    @Column(name = "unit_id")
    private UUID idUnidade;

    @Column(name = "reporter_user_id", updatable = false)
    private UUID idUsuarioSolicitante;

    @Column(name = "reporter_name", nullable = false, updatable = false)
    private String nomeSolicitante;

    @Column(name = "assignee_name")
    private String nomeResponsavel;

    @Column(name = "due_date")
    private LocalDate prazo;

    @Column(name = "resolution")
    private String solucao;

    @Column(name = "resolved_at")
    private Instant resolvidaEm;

    protected Ocorrencia() {
    }

    public Ocorrencia(UUID idAdministradora, UUID idCondominio, int numero, String titulo, String descricao,
                      Categoria categoria, Prioridade prioridade, UUID idUnidade, UUID idUsuarioSolicitante, String nomeSolicitante) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.numero = numero;
        this.titulo = titulo;
        this.descricao = descricao;
        this.categoria = categoria;
        this.prioridade = prioridade;
        this.idUnidade = idUnidade;
        this.idUsuarioSolicitante = idUsuarioSolicitante;
        this.nomeSolicitante = nomeSolicitante;
    }

    public void triar(Categoria categoria, Prioridade prioridade, String nomeResponsavel, LocalDate prazo) {
        exigirAberta();
        this.categoria = categoria;
        this.prioridade = prioridade;
        this.nomeResponsavel = nomeResponsavel;
        this.prazo = prazo;
    }

    public void alterarSituacao(Situacao nova, String solucao, Instant agora) {
        exigirAberta();
        if (nova == Situacao.RESOLVED && (solucao == null || solucao.isBlank())) {
            throw ApiException.requisicaoInvalida("RESOLUTION_REQUIRED", "Descreva a solução para encerrar a ocorrência.");
        }
        this.situacao = nova;
        if (nova.isEncerrada()) {
            this.solucao = solucao;
            this.resolvidaEm = agora;
        }
    }

    /** Reabre (ex.: o morador informa que o problema persiste). */
    public void reabrir() {
        if (!situacao.isEncerrada()) {
            throw ApiException.regraNegocio("OCCURRENCE_NOT_CLOSED", "A ocorrência não está encerrada.");
        }
        situacao = Situacao.OPEN;
        resolvidaEm = null;
    }

    public boolean isAtrasada(LocalDate hoje) {
        return !situacao.isEncerrada() && prazo != null && prazo.isBefore(hoje);
    }

    private void exigirAberta() {
        if (situacao.isEncerrada()) {
            throw ApiException.regraNegocio("OCCURRENCE_CLOSED", "A ocorrência está encerrada. Reabra-a para alterar.");
        }
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public int getNumero() {
        return numero;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public UUID getIdUnidade() {
        return idUnidade;
    }

    public UUID getIdUsuarioSolicitante() {
        return idUsuarioSolicitante;
    }

    public String getNomeSolicitante() {
        return nomeSolicitante;
    }

    public String getNomeResponsavel() {
        return nomeResponsavel;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public String getSolucao() {
        return solucao;
    }

    public Instant getResolvidaEm() {
        return resolvidaEm;
    }
}
