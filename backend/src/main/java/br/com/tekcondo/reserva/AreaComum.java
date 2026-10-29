package br.com.tekcondo.reserva;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Área comum reservável e suas regras de uso. */
@Entity
@Table(name = "common_area")
public class AreaComum extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "description")
    private String descricao;

    @Column(name = "rules")
    private String regras;

    @Column(name = "capacity")
    private Integer capacidade;

    @Column(name = "fee", nullable = false)
    private BigDecimal taxa = BigDecimal.ZERO;

    @Column(name = "requires_approval", nullable = false)
    private boolean exigeAprovacao;

    @Column(name = "block_delinquent", nullable = false)
    private boolean bloqueiaInadimplente;

    @Convert(converter = MinutoDoDia.class)
    @Column(name = "open_minute", nullable = false)
    private LocalTime horaAbertura;

    @Convert(converter = MinutoDoDia.class)
    @Column(name = "close_minute", nullable = false)
    private LocalTime horaFechamento;

    @Column(name = "max_duration_minutes", nullable = false)
    private int duracaoMaximaMinutos;

    @Column(name = "min_advance_hours", nullable = false)
    private int antecedenciaMinimaHoras;

    @Column(name = "max_advance_days", nullable = false)
    private int antecedenciaMaximaDias;

    @Column(name = "max_per_unit_month")
    private Integer limiteMensalPorUnidade;

    @Column(name = "active", nullable = false)
    private boolean ativa = true;

    protected AreaComum() {
    }

    public AreaComum(UUID idAdministradora, UUID idCondominio) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
    }

    public void atualizar(String nome, String descricao, String regras, Integer capacidade, BigDecimal taxa, boolean exigeAprovacao,
                       boolean bloqueiaInadimplente, LocalTime horaAbertura, LocalTime horaFechamento, int duracaoMaximaMinutos, int antecedenciaMinimaHoras,
                       int antecedenciaMaximaDias, Integer limiteMensalPorUnidade) {
        this.nome = nome;
        this.descricao = descricao;
        this.regras = regras;
        this.capacidade = capacidade;
        this.taxa = taxa == null ? BigDecimal.ZERO : taxa;
        this.exigeAprovacao = exigeAprovacao;
        this.bloqueiaInadimplente = bloqueiaInadimplente;
        this.horaAbertura = horaAbertura;
        this.horaFechamento = horaFechamento;
        this.duracaoMaximaMinutos = duracaoMaximaMinutos;
        this.antecedenciaMinimaHoras = antecedenciaMinimaHoras;
        this.antecedenciaMaximaDias = antecedenciaMaximaDias;
        this.limiteMensalPorUnidade = limiteMensalPorUnidade;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getRegras() {
        return regras;
    }

    public Integer getCapacidade() {
        return capacidade;
    }

    public BigDecimal getTaxa() {
        return taxa;
    }

    public boolean isExigeAprovacao() {
        return exigeAprovacao;
    }

    public boolean isBloqueiaInadimplente() {
        return bloqueiaInadimplente;
    }

    public LocalTime getHoraAbertura() {
        return horaAbertura;
    }

    public LocalTime getHoraFechamento() {
        return horaFechamento;
    }

    public int getDuracaoMaximaMinutos() {
        return duracaoMaximaMinutos;
    }

    public int getAntecedenciaMinimaHoras() {
        return antecedenciaMinimaHoras;
    }

    public int getAntecedenciaMaximaDias() {
        return antecedenciaMaximaDias;
    }

    public Integer getLimiteMensalPorUnidade() {
        return limiteMensalPorUnidade;
    }

    public boolean isAtiva() {
        return ativa;
    }
}
