package br.com.tekcondo.financeiro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_transfer")
public class Transferencia {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", updatable = false)
    private ContaBancaria contaOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", updatable = false)
    private ContaBancaria contaDestino;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal valor;

    @Column(name = "transfer_date", nullable = false, updatable = false)
    private LocalDate data;

    @Column(name = "description")
    private String descricao;

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

    protected Transferencia() {
    }

    public Transferencia(UUID idAdministradora, UUID idCondominio, ContaBancaria origem, ContaBancaria destino, BigDecimal valor,
                         LocalDate data, String descricao, UUID criadoPor) {
        if (origem.getId().equals(destino.getId())) {
            throw ApiException.regraNegocio("SAME_ACCOUNT", "As contas de origem e destino devem ser diferentes.");
        }
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.contaOrigem = origem;
        this.contaDestino = destino;
        this.valor = valor;
        this.data = data;
        this.descricao = descricao;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public void estornar(String motivo, UUID por, Instant agora) {
        if (estornadaEm != null) {
            throw ApiException.regraNegocio("TRANSFER_ALREADY_REVERSED", "Esta transferência já foi estornada.");
        }
        if (conciliadaEm != null) {
            throw ApiException.regraNegocio("TRANSFER_RECONCILED", "Desfaça a conciliação antes de estornar.");
        }
        estornadaEm = agora;
        estornadaPor = por;
        motivoEstorno = motivo;
    }

    public void conciliar(boolean conciliada, UUID por, Instant agora) {
        if (estornadaEm != null) {
            throw ApiException.regraNegocio("TRANSFER_REVERSED", "Transferência estornada não pode ser conciliada.");
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

    public ContaBancaria getContaOrigem() {
        return contaOrigem;
    }

    public ContaBancaria getContaDestino() {
        return contaDestino;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
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
}
