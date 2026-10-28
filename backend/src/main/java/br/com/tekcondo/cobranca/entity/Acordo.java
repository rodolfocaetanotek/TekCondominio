package br.com.tekcondo.cobranca.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Acordo de parcelamento de débitos de uma unidade. */
@Entity
@Table(name = "agreement")
public class Acordo {

    public enum Situacao {
        ACTIVE, COMPLETED, CANCELLED
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID idUnidade;

    @Column(name = "person_id", updatable = false)
    private UUID idPessoa;

    @Column(name = "original_amount", nullable = false, updatable = false)
    private BigDecimal valorOriginal;

    @Column(name = "charges_amount", nullable = false, updatable = false)
    private BigDecimal valorEncargos;

    @Column(name = "discount", nullable = false, updatable = false)
    private BigDecimal desconto;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private BigDecimal valorTotal;

    @Column(name = "installments", nullable = false, updatable = false)
    private int totalParcelas;

    @Column(name = "first_due_date", nullable = false, updatable = false)
    private LocalDate dataPrimeiroVencimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao = Situacao.ACTIVE;

    @Column(name = "notes")
    private String observacoes;

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

    @Version
    @Column(name = "version")
    private Long versao;

    protected Acordo() {
    }

    public Acordo(UUID idAdministradora, UUID idCondominio, UUID idUnidade, UUID idPessoa, BigDecimal valorOriginal,
                  BigDecimal valorEncargos, BigDecimal desconto, int totalParcelas, LocalDate dataPrimeiroVencimento,
                  String observacoes, UUID criadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.idUnidade = idUnidade;
        this.idPessoa = idPessoa;
        this.valorOriginal = valorOriginal;
        this.valorEncargos = valorEncargos;
        this.desconto = desconto;
        this.valorTotal = valorOriginal.add(valorEncargos).subtract(desconto);
        if (valorTotal.signum() <= 0) {
            throw ApiException.regraNegocio("INVALID_AGREEMENT_TOTAL", "O desconto não pode zerar o valor do acordo.");
        }
        this.totalParcelas = totalParcelas;
        this.dataPrimeiroVencimento = dataPrimeiroVencimento;
        this.observacoes = observacoes;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public void cancelar(String motivo, UUID por, Instant agora) {
        if (situacao != Situacao.ACTIVE) {
            throw ApiException.regraNegocio("AGREEMENT_NOT_ACTIVE", "Somente acordos ativos podem ser cancelados.");
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

    public UUID getIdUnidade() {
        return idUnidade;
    }

    public UUID getIdPessoa() {
        return idPessoa;
    }

    public BigDecimal getValorOriginal() {
        return valorOriginal;
    }

    public BigDecimal getValorEncargos() {
        return valorEncargos;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public int getTotalParcelas() {
        return totalParcelas;
    }

    public LocalDate getDataPrimeiroVencimento() {
        return dataPrimeiroVencimento;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
