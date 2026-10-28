package br.com.tekcondo.cobranca.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.integracao.banco.RetornoCnab240Parser;
import br.com.tekcondo.comum.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Título informado no retorno do banco. SETTLED = baixado automaticamente; PENDING = liquidação que não pôde ser
 * baixada (título não encontrado, já pago, valor divergente) e aguarda tratamento; INFO = movimento sem efeito
 * financeiro (entrada confirmada, baixa, tarifa); IGNORED = pendência tratada manualmente.
 */
@Entity
@Table(name = "bank_return_item")
public class ItemRetorno {

    public enum Situacao {
        SETTLED, PENDING, INFO, IGNORED
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID idContaBancaria;

    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID idArquivo;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequencia;

    @Column(name = "movement_code", nullable = false, updatable = false)
    private String codigoMovimento;

    @Column(name = "our_number", updatable = false)
    private String nossoNumero;

    @Column(name = "your_number", updatable = false)
    private String seuNumero;

    @Column(name = "due_date", updatable = false)
    private LocalDate dataVencimento;

    @Column(name = "face_value", nullable = false, updatable = false)
    private BigDecimal valorTitulo;

    @Column(name = "fees", nullable = false, updatable = false)
    private BigDecimal encargos;

    @Column(name = "discount", nullable = false, updatable = false)
    private BigDecimal desconto;

    @Column(name = "paid_amount", nullable = false, updatable = false)
    private BigDecimal valorPago;

    @Column(name = "bank_fee", nullable = false, updatable = false)
    private BigDecimal tarifa;

    @Column(name = "occurrence_date", updatable = false)
    private LocalDate dataOcorrencia;

    @Column(name = "credit_date", updatable = false)
    private LocalDate dataCredito;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao;

    @Column(name = "entry_id")
    private UUID idLancamento;

    @Column(name = "payment_id")
    private UUID idBaixa;

    @Column(name = "message")
    private String mensagem;

    @Column(name = "resolved_at")
    private Instant resolvidoEm;

    @Column(name = "resolved_by")
    private UUID resolvidoPor;

    protected ItemRetorno() {
    }

    public ItemRetorno(UUID idAdministradora, UUID idCondominio, UUID idContaBancaria, UUID idArquivo, RetornoCnab240Parser.Item i) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.idContaBancaria = idContaBancaria;
        this.idArquivo = idArquivo;
        this.sequencia = i.sequencia();
        this.codigoMovimento = i.codigoMovimento();
        this.nossoNumero = i.nossoNumero();
        this.seuNumero = i.seuNumero();
        this.dataVencimento = i.dataVencimento();
        this.valorTitulo = i.valorTitulo();
        this.encargos = i.encargos();
        this.desconto = i.desconto();
        this.valorPago = i.valorPago();
        this.tarifa = i.tarifa();
        this.dataOcorrencia = i.dataOcorrencia();
        this.dataCredito = i.dataCredito();
        this.situacao = i.isLiquidacao() ? Situacao.PENDING : Situacao.INFO;
    }

    public void baixado(UUID idLancamento, UUID idBaixa) {
        this.situacao = Situacao.SETTLED;
        this.idLancamento = idLancamento;
        this.idBaixa = idBaixa;
        this.mensagem = null;
    }

    public void pendente(UUID idLancamento, String mensagem) {
        this.situacao = Situacao.PENDING;
        this.idLancamento = idLancamento;
        this.mensagem = mensagem;
    }

    public void informativo(String mensagem) {
        this.mensagem = mensagem;
    }

    public void ignorar(String observacao, UUID por, Instant agora) {
        if (situacao != Situacao.PENDING) {
            throw ApiException.regraNegocio("RETURN_ITEM_NOT_PENDING", "Somente pendências podem ser descartadas.");
        }
        situacao = Situacao.IGNORED;
        mensagem = observacao;
        resolvidoPor = por;
        resolvidoEm = agora;
    }

    /** Data em que o valor entrou na conta (crédito); na falta, a data da ocorrência. */
    public LocalDate dataLiquidacao() {
        return dataCredito != null ? dataCredito : dataOcorrencia;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdContaBancaria() {
        return idContaBancaria;
    }

    public UUID getIdArquivo() {
        return idArquivo;
    }

    public int getSequencia() {
        return sequencia;
    }

    public String getCodigoMovimento() {
        return codigoMovimento;
    }

    public String getNossoNumero() {
        return nossoNumero;
    }

    public String getSeuNumero() {
        return seuNumero;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public BigDecimal getValorTitulo() {
        return valorTitulo;
    }

    public BigDecimal getEncargos() {
        return encargos;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public BigDecimal getTarifa() {
        return tarifa;
    }

    public LocalDate getDataOcorrencia() {
        return dataOcorrencia;
    }

    public LocalDate getDataCredito() {
        return dataCredito;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public UUID getIdLancamento() {
        return idLancamento;
    }

    public UUID getIdBaixa() {
        return idBaixa;
    }

    public String getMensagem() {
        return mensagem;
    }
}
