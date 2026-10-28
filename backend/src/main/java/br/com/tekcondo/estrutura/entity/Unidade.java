package br.com.tekcondo.estrutura.entity;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Unidade autônoma (apartamento, casa, sala, loja, vaga...). Fração ideal em percentual (0–100, 6 casas). */
@Entity
@Table(name = "unit")
public class Unidade extends AuditableEntity {

    public enum Tipo {
        APARTMENT, HOUSE, ROOM, STORE, PARKING, OTHER
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Bloco bloco;

    @Column(name = "identifier", nullable = false)
    private String identificacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Tipo tipo;

    @Column(name = "floor")
    private String andar;

    @Column(name = "area")
    private BigDecimal area;

    @Column(name = "ideal_fraction", nullable = false)
    private BigDecimal fracaoIdeal = BigDecimal.ZERO;

    @Column(name = "notes")
    private String observacoes;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected Unidade() {
    }

    public Unidade(UUID idAdministradora, UUID idCondominio) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
    }

    public void atualizar(Bloco bloco, String identificacao, Tipo tipo, String andar, BigDecimal area,
                          BigDecimal fracaoIdeal, String observacoes) {
        this.bloco = bloco;
        this.identificacao = identificacao.trim();
        this.tipo = tipo;
        this.andar = emBranco(andar);
        this.area = area;
        this.fracaoIdeal = fracaoIdeal == null ? BigDecimal.ZERO : fracaoIdeal;
        this.observacoes = emBranco(observacoes);
    }

    /** Rótulo legível: "Bloco A · 101" ou "101". */
    public String rotulo() {
        return bloco == null ? identificacao : bloco.getNome() + " · " + identificacao;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    private static String emBranco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public Bloco getBloco() {
        return bloco;
    }

    public String getIdentificacao() {
        return identificacao;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getAndar() {
        return andar;
    }

    public BigDecimal getArea() {
        return area;
    }

    public BigDecimal getFracaoIdeal() {
        return fracaoIdeal;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
