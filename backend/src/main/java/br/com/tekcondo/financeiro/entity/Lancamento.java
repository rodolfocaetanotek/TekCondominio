package br.com.tekcondo.financeiro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.supplier.entity.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Título financeiro: conta a pagar (EXPENSE) ou a receber (INCOME). Cobranças condominiais são títulos a receber
 * vinculados a uma unidade. O saldo pago só muda por baixas e estornos registrados.
 */
@Entity
@Table(name = "financial_entry")
public class Lancamento {

    public enum Natureza {
        INCOME, EXPENSE
    }

    public enum Situacao {
        OPEN, PARTIAL, PAID, CANCELLED, RENEGOTIATED
    }

    public enum Aprovacao {
        NOT_REQUIRED, PENDING, APPROVED, REJECTED
    }

    public enum Origem {
        MANUAL, RECURRENCE, ALLOCATION, AGREEMENT
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private Natureza natureza;

    @Column(name = "description", nullable = false)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chart_account_id")
    private ContaPlano contaPlano;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CentroCusto centroCusto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier fornecedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Pessoa pessoa;

    @Column(name = "amount", nullable = false)
    private BigDecimal valor;

    @Column(name = "paid_amount", nullable = false)
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "competence", nullable = false)
    private LocalDate competencia;

    @Column(name = "document_number")
    private String numeroDocumento;

    @Column(name = "notes")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao = Situacao.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval", nullable = false)
    private Aprovacao aprovacao = Aprovacao.NOT_REQUIRED;

    @Column(name = "approved_by")
    private UUID aprovadoPor;

    @Column(name = "approved_at")
    private Instant aprovadoEm;

    @Column(name = "approval_note")
    private String observacaoAprovacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false)
    private Origem origem = Origem.MANUAL;

    @Column(name = "recurrence_group", updatable = false)
    private UUID grupoRecorrencia;

    @Column(name = "installment", updatable = false)
    private Integer parcela;

    @Column(name = "installments", updatable = false)
    private Integer totalParcelas;

    @Column(name = "allocation_id", updatable = false)
    private UUID idRateio;

    @Column(name = "agreement_id", updatable = false)
    private UUID idAcordo;

    @Column(name = "fine_percent")
    private BigDecimal percentualMulta;

    @Column(name = "interest_monthly_percent")
    private BigDecimal percentualJurosMensal;

    @Column(name = "cancelled_at")
    private Instant canceladoEm;

    @Column(name = "cancelled_by")
    private UUID canceladoPor;

    @Column(name = "cancel_reason")
    private String motivoCancelamento;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "updated_at", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "updated_by")
    private UUID atualizadoPor;

    /** Número de cobrança (gerado pelo banco de dados): enviado ao banco no boleto e usado na baixa pelo retorno. */
    @Generated(event = EventType.INSERT)
    @Column(name = "collection_number", insertable = false, updatable = false)
    private Long numeroCobranca;

    @Version
    @Column(name = "version")
    private Long versao;

    protected Lancamento() {
    }

    public Lancamento(UUID idAdministradora, UUID idCondominio, Natureza natureza, Origem origem, UUID criadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.natureza = natureza;
        this.origem = origem;
        this.criadoPor = criadoPor;
        this.atualizadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    // ------------------------------------------------------------------ regras

    public void atualizar(String descricao, ContaPlano conta, CentroCusto centroCusto, Supplier fornecedor, Unidade unidade,
                          Pessoa pessoa, BigDecimal valor, LocalDate dataVencimento, LocalDate competencia,
                          String numeroDocumento, String observacoes, UUID por) {
        if (valorPago.signum() > 0) {
            throw ApiException.regraNegocio("ENTRY_HAS_PAYMENTS",
                    "O lançamento possui baixas. Estorne as baixas antes de alterá-lo.");
        }
        exigirAlteravel();
        this.descricao = descricao.trim();
        this.contaPlano = conta;
        this.centroCusto = centroCusto;
        this.fornecedor = fornecedor;
        this.unidade = unidade;
        this.pessoa = pessoa;
        this.valor = valor;
        this.dataVencimento = dataVencimento;
        this.competencia = competencia.withDayOfMonth(1);
        this.numeroDocumento = numeroDocumento;
        this.observacoes = observacoes;
        this.atualizadoPor = por;
    }

    public void recorrencia(UUID grupo, int parcela, int totalParcelas) {
        this.grupoRecorrencia = grupo;
        this.parcela = parcela;
        this.totalParcelas = totalParcelas;
    }

    public void vincularRateio(UUID idRateio) {
        this.idRateio = idRateio;
    }

    public void vincularAcordo(UUID idAcordo) {
        this.idAcordo = idAcordo;
    }

    public void definirEncargos(BigDecimal percentualMulta, BigDecimal percentualJurosMensal) {
        this.percentualMulta = percentualMulta;
        this.percentualJurosMensal = percentualJurosMensal;
    }

    public void exigirAprovacao(boolean exigida) {
        this.aprovacao = exigida ? Aprovacao.PENDING : Aprovacao.NOT_REQUIRED;
    }

    public BigDecimal valorAberto() {
        return valor.subtract(valorPago);
    }

    public boolean isVencido(LocalDate hoje) {
        return (situacao == Situacao.OPEN || situacao == Situacao.PARTIAL) && dataVencimento.isBefore(hoje);
    }

    public void aprovar(UUID por, Instant agora, String observacao) {
        if (aprovacao != Aprovacao.PENDING) {
            throw ApiException.regraNegocio("ENTRY_NOT_PENDING", "Este lançamento não está aguardando aprovação.");
        }
        aprovacao = Aprovacao.APPROVED;
        aprovadoPor = por;
        aprovadoEm = agora;
        observacaoAprovacao = observacao;
        atualizadoPor = por;
    }

    public void reprovar(UUID por, Instant agora, String observacao) {
        if (aprovacao != Aprovacao.PENDING) {
            throw ApiException.regraNegocio("ENTRY_NOT_PENDING", "Este lançamento não está aguardando aprovação.");
        }
        aprovacao = Aprovacao.REJECTED;
        aprovadoPor = por;
        aprovadoEm = agora;
        observacaoAprovacao = observacao;
        atualizadoPor = por;
    }

    /** Registra baixa (parcial ou total) do valor principal. */
    public void registrarBaixa(BigDecimal principal, UUID por) {
        if (situacao != Situacao.OPEN && situacao != Situacao.PARTIAL) {
            throw ApiException.regraNegocio("ENTRY_NOT_PAYABLE", "Somente lançamentos em aberto podem receber baixa.");
        }
        if (aprovacao == Aprovacao.PENDING || aprovacao == Aprovacao.REJECTED) {
            throw ApiException.regraNegocio("ENTRY_NOT_APPROVED",
                    aprovacao == Aprovacao.PENDING ? "O pagamento aguarda aprovação." : "O pagamento foi reprovado.");
        }
        if (principal.compareTo(valorAberto()) > 0) {
            throw ApiException.regraNegocio("PAYMENT_EXCEEDS_BALANCE",
                    "O valor da baixa excede o saldo em aberto (R$ " + valorAberto().toPlainString() + ").");
        }
        valorPago = valorPago.add(principal);
        situacao = valorPago.compareTo(valor) == 0 ? Situacao.PAID : Situacao.PARTIAL;
        atualizadoPor = por;
    }

    /** Desfaz o efeito de uma baixa estornada. */
    public void estornarBaixa(BigDecimal principal, UUID por) {
        if (situacao == Situacao.CANCELLED || situacao == Situacao.RENEGOTIATED) {
            throw ApiException.regraNegocio("ENTRY_CLOSED", "O lançamento está encerrado.");
        }
        valorPago = valorPago.subtract(principal);
        situacao = valorPago.signum() == 0 ? Situacao.OPEN : Situacao.PARTIAL;
        atualizadoPor = por;
    }

    public void cancelar(String motivo, UUID por, Instant agora) {
        if (situacao == Situacao.CANCELLED || situacao == Situacao.RENEGOTIATED) {
            throw ApiException.regraNegocio("ENTRY_ALREADY_CLOSED", "O lançamento já está cancelado ou renegociado.");
        }
        if (valorPago.signum() > 0) {
            throw ApiException.regraNegocio("ENTRY_HAS_PAYMENTS",
                    "O lançamento possui baixas. Estorne as baixas antes de cancelá-lo.");
        }
        situacao = Situacao.CANCELLED;
        motivoCancelamento = motivo;
        canceladoPor = por;
        canceladoEm = agora;
        atualizadoPor = por;
    }

    /** Título substituído por acordo de parcelamento. */
    public void renegociar(UUID idAcordo, UUID por) {
        if (situacao != Situacao.OPEN && situacao != Situacao.PARTIAL) {
            throw ApiException.regraNegocio("ENTRY_NOT_OPEN", "Somente títulos em aberto podem ser renegociados.");
        }
        situacao = Situacao.RENEGOTIATED;
        motivoCancelamento = "Renegociado no acordo " + idAcordo;
        atualizadoPor = por;
    }

    /** Desfaz a renegociação (acordo cancelado sem pagamentos): o título volta a ficar em aberto. */
    public void restaurarDoAcordo(UUID por) {
        if (situacao != Situacao.RENEGOTIATED) {
            return;
        }
        situacao = valorPago.signum() == 0 ? Situacao.OPEN : Situacao.PARTIAL;
        motivoCancelamento = null;
        atualizadoPor = por;
    }

    private void exigirAlteravel() {
        if (situacao == Situacao.CANCELLED || situacao == Situacao.RENEGOTIATED || situacao == Situacao.PAID) {
            throw ApiException.regraNegocio("ENTRY_CLOSED", "Lançamento encerrado não pode ser alterado.");
        }
    }

    // ------------------------------------------------------------------ getters

    public UUID getId() {
        return id;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public Natureza getNatureza() {
        return natureza;
    }

    public String getDescricao() {
        return descricao;
    }

    public ContaPlano getContaPlano() {
        return contaPlano;
    }

    public CentroCusto getCentroCusto() {
        return centroCusto;
    }

    public Supplier getFornecedor() {
        return fornecedor;
    }

    public Unidade getUnidade() {
        return unidade;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public LocalDate getCompetencia() {
        return competencia;
    }

    public Long getNumeroCobranca() {
        return numeroCobranca;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public Aprovacao getAprovacao() {
        return aprovacao;
    }

    public Instant getAprovadoEm() {
        return aprovadoEm;
    }

    public UUID getAprovadoPor() {
        return aprovadoPor;
    }

    public String getObservacaoAprovacao() {
        return observacaoAprovacao;
    }

    public Origem getOrigem() {
        return origem;
    }

    public UUID getGrupoRecorrencia() {
        return grupoRecorrencia;
    }

    public Integer getParcela() {
        return parcela;
    }

    public Integer getTotalParcelas() {
        return totalParcelas;
    }

    public UUID getIdRateio() {
        return idRateio;
    }

    public UUID getIdAcordo() {
        return idAcordo;
    }

    public BigDecimal getPercentualMulta() {
        return percentualMulta;
    }

    public BigDecimal getPercentualJurosMensal() {
        return percentualJurosMensal;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public Instant getCanceladoEm() {
        return canceladoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Long getVersao() {
        return versao;
    }
}
