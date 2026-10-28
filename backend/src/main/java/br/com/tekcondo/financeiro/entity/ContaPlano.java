package br.com.tekcondo.financeiro.entity;

import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Conta do plano de contas. idAdministradora nulo = conta do plano padrão do sistema. */
@Entity
@Table(name = "chart_account")
public class ContaPlano extends AuditableEntity {

    public enum Natureza {
        INCOME, EXPENSE
    }

    @Column(name = "organization_id", updatable = false)
    private UUID idAdministradora;

    @Column(name = "code", nullable = false)
    private String codigo;

    @Column(name = "name", nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false)
    private Natureza natureza;

    /** Analítica = aceita lançamentos; sintética = apenas agrupa. */
    @Column(name = "analytic", nullable = false)
    private boolean analitica = true;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected ContaPlano() {
    }

    public ContaPlano(UUID idAdministradora, String codigo, String nome, Natureza natureza) {
        this.idAdministradora = idAdministradora;
        this.codigo = codigo.trim();
        this.nome = nome.trim();
        this.natureza = natureza;
    }

    public void renomear(String nome) {
        this.nome = nome.trim();
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isSistema() {
        return idAdministradora == null;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public Natureza getNatureza() {
        return natureza;
    }

    public boolean isAnalitica() {
        return analitica;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
