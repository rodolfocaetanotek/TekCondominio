package br.com.tekcondo.financeiro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Valor orçado para uma conta em um mês do ano. */
@Entity
@Table(name = "budget_line")
public class LinhaOrcamento {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "year", nullable = false, updatable = false)
    private int ano;

    @Column(name = "month", nullable = false, updatable = false)
    private int mes;

    @Column(name = "chart_account_id", nullable = false, updatable = false)
    private UUID idContaPlano;

    @Column(name = "amount", nullable = false)
    private BigDecimal valor;

    @Column(name = "updated_at", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "updated_by")
    private UUID atualizadoPor;

    protected LinhaOrcamento() {
    }

    public LinhaOrcamento(UUID idAdministradora, UUID idCondominio, int ano, int mes, UUID idContaPlano) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.ano = ano;
        this.mes = mes;
        this.idContaPlano = idContaPlano;
    }

    public void setValor(BigDecimal valor, UUID por) {
        this.valor = valor;
        this.atualizadoPor = por;
        this.atualizadoEm = Instant.now();
    }

    public int getMes() {
        return mes;
    }

    public UUID getIdContaPlano() {
        return idContaPlano;
    }

    public BigDecimal getValor() {
        return valor;
    }
}
