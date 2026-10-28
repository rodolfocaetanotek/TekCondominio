package br.com.tekcondo.cobranca.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.cobranca.service.RateioCalculator;
import br.com.tekcondo.comum.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Rateio de uma competência: origem das cobranças (títulos a receber) de cada unidade. */
@Entity
@Table(name = "allocation")
public class Rateio {

    public enum Situacao {
        GENERATED, CANCELLED
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "competence", nullable = false, updatable = false)
    private LocalDate competencia;

    @Column(name = "description", nullable = false, updatable = false)
    private String descricao;

    @Column(name = "chart_account_id", nullable = false, updatable = false)
    private UUID idContaPlano;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false)
    private RateioCalculator.Criterio criterio;

    @Column(name = "base_amount", nullable = false, updatable = false)
    private BigDecimal valorBase;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dataVencimento;

    @Column(name = "units_count", nullable = false, updatable = false)
    private int quantidadeUnidades;

    @Column(name = "generated_amount", nullable = false, updatable = false)
    private BigDecimal valorGerado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao = Situacao.GENERATED;

    @Column(name = "cancel_reason")
    private String motivoCancelamento;

    @Column(name = "cancelled_at")
    private Instant canceladoEm;

    @Column(name = "cancelled_by")
    private UUID canceladoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    protected Rateio() {
    }

    public Rateio(UUID idAdministradora, UUID idCondominio, LocalDate competencia, String descricao, UUID idContaPlano,
                  RateioCalculator.Criterio criterio, BigDecimal valorBase, LocalDate dataVencimento, int quantidadeUnidades,
                  BigDecimal valorGerado, UUID criadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.competencia = competencia.withDayOfMonth(1);
        this.descricao = descricao;
        this.idContaPlano = idContaPlano;
        this.criterio = criterio;
        this.valorBase = valorBase;
        this.dataVencimento = dataVencimento;
        this.quantidadeUnidades = quantidadeUnidades;
        this.valorGerado = valorGerado;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public void cancelar(String motivo, UUID por, Instant agora) {
        if (situacao == Situacao.CANCELLED) {
            throw ApiException.regraNegocio("ALLOCATION_ALREADY_CANCELLED", "O rateio já está cancelado.");
        }
        situacao = Situacao.CANCELLED;
        motivoCancelamento = motivo;
        canceladoPor = por;
        canceladoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public LocalDate getCompetencia() {
        return competencia;
    }

    public String getDescricao() {
        return descricao;
    }

    public UUID getIdContaPlano() {
        return idContaPlano;
    }

    public RateioCalculator.Criterio getCriterio() {
        return criterio;
    }

    public BigDecimal getValorBase() {
        return valorBase;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public int getQuantidadeUnidades() {
        return quantidadeUnidades;
    }

    public BigDecimal getValorGerado() {
        return valorGerado;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
