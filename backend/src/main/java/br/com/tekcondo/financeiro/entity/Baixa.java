package br.com.tekcondo.financeiro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Baixa de um título. Estorno é lógico (estornadaEm + motivo), preservando a trilha. */
@Entity
@Table(name = "entry_payment")
public class Baixa {

    public enum FormaPagamento {
        PIX, BOLETO, TRANSFER, CASH, CHECK, DEBIT, CARD, OTHER
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", updatable = false)
    private Lancamento lancamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", updatable = false)
    private ContaBancaria contaBancaria;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate dataPagamento;

    @Column(name = "principal", nullable = false, updatable = false)
    private BigDecimal principal;

    @Column(name = "fine", nullable = false, updatable = false)
    private BigDecimal multa;

    @Column(name = "interest", nullable = false, updatable = false)
    private BigDecimal juros;

    @Column(name = "discount", nullable = false, updatable = false)
    private BigDecimal desconto;

    @Column(name = "total", nullable = false, updatable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false)
    private FormaPagamento formaPagamento;

    @Column(name = "notes")
    private String observacoes;

    @Column(name = "reconciled_at")
    private Instant conciliadaEm;

    @Column(name = "reconciled_by")
    private UUID conciliadaPor;

    @Column(name = "reversed_at")
    private Instant estornadaEm;

    @Column(name = "reversed_by")
    private UUID estornadaPor;

    @Column(name = "reversal_reason")
    private String motivoEstorno;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    protected Baixa() {
    }

    public Baixa(Lancamento lancamento, ContaBancaria contaBancaria, LocalDate dataPagamento, BigDecimal principal,
                 BigDecimal multa, BigDecimal juros, BigDecimal desconto, FormaPagamento formaPagamento, String observacoes,
                 UUID criadoPor) {
        this.idAdministradora = lancamento.getIdAdministradora();
        this.idCondominio = lancamento.getIdCondominio();
        this.lancamento = lancamento;
        this.contaBancaria = contaBancaria;
        this.dataPagamento = dataPagamento;
        this.principal = principal;
        this.multa = multa;
        this.juros = juros;
        this.desconto = desconto;
        this.total = principal.add(multa).add(juros).subtract(desconto);
        if (total.signum() < 0) {
            throw ApiException.regraNegocio("INVALID_DISCOUNT", "O desconto não pode ser maior que o valor pago.");
        }
        this.formaPagamento = formaPagamento;
        this.observacoes = observacoes;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public boolean isEstornada() {
        return estornadaEm != null;
    }

    public void estornar(String motivo, UUID por, Instant agora) {
        if (estornadaEm != null) {
            throw ApiException.regraNegocio("PAYMENT_ALREADY_REVERSED", "Esta baixa já foi estornada.");
        }
        if (conciliadaEm != null) {
            throw ApiException.regraNegocio("PAYMENT_RECONCILED",
                    "A baixa está conciliada com o extrato. Desfaça a conciliação antes de estornar.");
        }
        estornadaEm = agora;
        estornadaPor = por;
        motivoEstorno = motivo;
    }

    public void conciliar(boolean conciliada, UUID por, Instant agora) {
        if (estornadaEm != null) {
            throw ApiException.regraNegocio("PAYMENT_REVERSED", "Baixa estornada não pode ser conciliada.");
        }
        conciliadaEm = conciliada ? agora : null;
        conciliadaPor = conciliada ? por : null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public Lancamento getLancamento() {
        return lancamento;
    }

    public ContaBancaria getContaBancaria() {
        return contaBancaria;
    }

    public LocalDate getDataPagamento() {
        return dataPagamento;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getMulta() {
        return multa;
    }

    public BigDecimal getJuros() {
        return juros;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Instant getConciliadaEm() {
        return conciliadaEm;
    }

    public Instant getEstornadaEm() {
        return estornadaEm;
    }

    public String getMotivoEstorno() {
        return motivoEstorno;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
