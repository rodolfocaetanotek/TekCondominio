package br.com.tekcondo.financeiro.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Conta bancária ou caixa do condomínio. O saldo é sempre calculado a partir das movimentações. */
@Entity
@Table(name = "bank_account")
public class ContaBancaria extends AuditableEntity {

    public enum Tipo {
        CHECKING, SAVINGS, INVESTMENT, CASH
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "name", nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Tipo tipo;

    @Column(name = "bank_code")
    private String codigoBanco;

    @Column(name = "agency")
    private String agencia;

    @Column(name = "account_number")
    private String numeroConta;

    @Column(name = "opening_balance", nullable = false, updatable = false)
    private BigDecimal saldoInicial;

    @Column(name = "opening_date", nullable = false, updatable = false)
    private LocalDate dataAbertura;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected ContaBancaria() {
    }

    public ContaBancaria(UUID idAdministradora, UUID idCondominio, Tipo tipo, BigDecimal saldoInicial, LocalDate dataAbertura) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.tipo = tipo;
        this.saldoInicial = saldoInicial;
        this.dataAbertura = dataAbertura;
    }

    /** Saldo inicial e data de abertura não mudam após o cadastro (integridade do histórico). */
    public void atualizar(String nome, String codigoBanco, String agencia, String numeroConta) {
        this.nome = nome.trim();
        this.codigoBanco = codigoBanco;
        this.agencia = agencia;
        this.numeroConta = numeroConta;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public String getNome() {
        return nome;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getCodigoBanco() {
        return codigoBanco;
    }

    public String getAgencia() {
        return agencia;
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public LocalDate getDataAbertura() {
        return dataAbertura;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
