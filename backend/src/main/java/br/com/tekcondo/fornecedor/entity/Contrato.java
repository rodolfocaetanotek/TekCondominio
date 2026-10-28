package br.com.tekcondo.fornecedor.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Contrato de prestação de serviço/fornecimento vinculado ao condomínio. */
@Entity
@Table(name = "contract")
public class Contrato extends AuditableEntity {

    public enum Periodicidade {
        MONTHLY, YEARLY, ONE_TIME, OTHER
    }

    public enum Situacao {
        ACTIVE, TERMINATED
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Fornecedor fornecedor;

    @Column(name = "title", nullable = false)
    private String titulo;

    @Column(name = "description")
    private String descricao;

    @Column(name = "amount", nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", nullable = false)
    private Periodicidade periodicidade;

    @Column(name = "start_date", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "end_date")
    private LocalDate dataFim;

    @Column(name = "readjustment_index")
    private String indiceReajuste;

    @Column(name = "readjustment_month")
    private Integer mesReajuste;

    @Column(name = "notice_days", nullable = false)
    private int diasAvisoPrevio = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao = Situacao.ACTIVE;

    @Column(name = "terminated_on")
    private LocalDate dataEncerramento;

    @Column(name = "termination_reason")
    private String motivoEncerramento;

    protected Contrato() {
    }

    public Contrato(UUID idAdministradora, UUID idCondominio) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
    }

    public void atualizar(Fornecedor fornecedor, String titulo, String descricao, BigDecimal valor, Periodicidade periodicidade,
                          LocalDate dataInicio, LocalDate dataFim, String indiceReajuste, Integer mesReajuste, int diasAvisoPrevio) {
        if (situacao == Situacao.TERMINATED) {
            throw ApiException.regraNegocio("CONTRACT_TERMINATED", "Contrato encerrado não pode ser alterado.");
        }
        if (dataFim != null && dataFim.isBefore(dataInicio)) {
            throw ApiException.regraNegocio("CONTRACT_DATES", "O término deve ser posterior ao início.");
        }
        this.fornecedor = fornecedor;
        this.titulo = titulo;
        this.descricao = descricao;
        this.valor = valor;
        this.periodicidade = periodicidade;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.indiceReajuste = indiceReajuste;
        this.mesReajuste = mesReajuste;
        this.diasAvisoPrevio = diasAvisoPrevio;
    }

    public void encerrar(LocalDate data, String motivo) {
        if (situacao == Situacao.TERMINATED) {
            throw ApiException.regraNegocio("CONTRACT_TERMINATED", "O contrato já está encerrado.");
        }
        situacao = Situacao.TERMINATED;
        dataEncerramento = data;
        motivoEncerramento = motivo;
    }

    /** Vencido: ativo e com término no passado. A vencer: término dentro do prazo de aviso prévio. */
    public boolean isVencido(LocalDate hoje) {
        return situacao == Situacao.ACTIVE && dataFim != null && dataFim.isBefore(hoje);
    }

    public boolean isAVencer(LocalDate hoje) {
        return situacao == Situacao.ACTIVE && dataFim != null && !dataFim.isBefore(hoje) && !dataFim.isAfter(hoje.plusDays(diasAvisoPrevio));
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public Fornecedor getFornecedor() {
        return fornecedor;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public String getIndiceReajuste() {
        return indiceReajuste;
    }

    public Integer getMesReajuste() {
        return mesReajuste;
    }

    public int getDiasAvisoPrevio() {
        return diasAvisoPrevio;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public LocalDate getDataEncerramento() {
        return dataEncerramento;
    }

    public String getMotivoEncerramento() {
        return motivoEncerramento;
    }
}
