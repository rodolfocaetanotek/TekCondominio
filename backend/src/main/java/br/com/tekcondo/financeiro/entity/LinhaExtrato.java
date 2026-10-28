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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Transação do extrato do banco e sua situação na conciliação (casada com uma baixa/transferência, ou ignorada).
 * {@code fitId} mantém o nome do campo FITID do layout OFX.
 */
@Entity
@Table(name = "bank_statement_line")
public class LinhaExtrato {

    public enum Situacao {
        UNMATCHED, MATCHED, IGNORED
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

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID idImportacao;

    @Column(name = "fit_id", nullable = false, updatable = false)
    private String fitId;

    @Column(name = "posted_on", nullable = false, updatable = false)
    private LocalDate dataLancamento;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal valor;

    @Column(name = "trn_type", updatable = false)
    private String tipoTransacao;

    @Column(name = "memo", updatable = false)
    private String historico;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao = Situacao.UNMATCHED;

    @Column(name = "payment_id")
    private UUID idBaixa;

    @Column(name = "transfer_id")
    private UUID idTransferencia;

    @Column(name = "note")
    private String observacao;

    @Column(name = "resolved_at")
    private Instant resolvidaEm;

    @Column(name = "resolved_by")
    private UUID resolvidaPor;

    @Version
    @Column(name = "version")
    private Long versao;

    protected LinhaExtrato() {
    }

    public LinhaExtrato(UUID idAdministradora, UUID idCondominio, UUID idContaBancaria, UUID idImportacao, String fitId,
                        LocalDate dataLancamento, BigDecimal valor, String tipoTransacao, String historico) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.idContaBancaria = idContaBancaria;
        this.idImportacao = idImportacao;
        this.fitId = fitId;
        this.dataLancamento = dataLancamento;
        this.valor = valor;
        this.tipoTransacao = tipoTransacao;
        this.historico = historico;
    }

    public void conciliarBaixa(UUID idBaixa, UUID por, Instant agora) {
        exigirPendente();
        this.situacao = Situacao.MATCHED;
        this.idBaixa = idBaixa;
        resolver(por, agora);
    }

    public void conciliarTransferencia(UUID idTransferencia, UUID por, Instant agora) {
        exigirPendente();
        this.situacao = Situacao.MATCHED;
        this.idTransferencia = idTransferencia;
        resolver(por, agora);
    }

    public void ignorar(String observacao, UUID por, Instant agora) {
        exigirPendente();
        this.situacao = Situacao.IGNORED;
        this.observacao = observacao;
        resolver(por, agora);
    }

    /** Volta a linha para pendente (desfaz casamento ou "ignorar"). */
    public void reabrir() {
        situacao = Situacao.UNMATCHED;
        idBaixa = null;
        idTransferencia = null;
        observacao = null;
        resolvidaEm = null;
        resolvidaPor = null;
    }

    private void exigirPendente() {
        if (situacao != Situacao.UNMATCHED) {
            throw ApiException.regraNegocio("STATEMENT_LINE_RESOLVED", "Esta linha do extrato já foi conciliada ou ignorada.");
        }
    }

    private void resolver(UUID por, Instant agora) {
        resolvidaPor = por;
        resolvidaEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdContaBancaria() {
        return idContaBancaria;
    }

    public String getFitId() {
        return fitId;
    }

    public LocalDate getDataLancamento() {
        return dataLancamento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getTipoTransacao() {
        return tipoTransacao;
    }

    public String getHistorico() {
        return historico;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public UUID getIdBaixa() {
        return idBaixa;
    }

    public UUID getIdTransferencia() {
        return idTransferencia;
    }

    public String getObservacao() {
        return observacao;
    }

    public Long getVersao() {
        return versao;
    }
}
