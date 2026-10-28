package br.com.tekcondo.pessoa.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
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

/** Vínculo pessoa ↔ unidade com período. Encerrado com data e motivo; nunca apagado (histórico de ocupação). */
@Entity
@Table(name = "unit_occupancy")
public class Vinculo {

    /** Tipo de vínculo: proprietário, inquilino ou morador. */
    public enum Tipo {
        OWNER, TENANT, RESIDENT
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id")
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id")
    private Pessoa pessoa;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Tipo tipo;

    @Column(name = "financial_responsible", nullable = false)
    private boolean responsavelFinanceiro;

    @Column(name = "start_date", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "end_date")
    private LocalDate dataFim;

    @Column(name = "end_reason")
    private String motivoEncerramento;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by")
    private UUID criadoPor;

    protected Vinculo() {
    }

    public Vinculo(Unidade unidade, Pessoa pessoa, Tipo tipo, boolean responsavelFinanceiro, LocalDate dataInicio,
                   UUID criadoPor) {
        this.idAdministradora = unidade.getIdAdministradora();
        this.unidade = unidade;
        this.pessoa = pessoa;
        this.tipo = tipo;
        this.responsavelFinanceiro = responsavelFinanceiro;
        this.dataInicio = dataInicio;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public boolean isVigente() {
        return dataFim == null;
    }

    public void encerrar(LocalDate data, String motivo) {
        if (dataFim != null) {
            throw ApiException.regraNegocio("OCCUPANCY_ALREADY_ENDED", "Este vínculo já foi encerrado.");
        }
        if (data.isBefore(dataInicio)) {
            throw ApiException.regraNegocio("OCCUPANCY_END_BEFORE_START",
                    "A data de encerramento não pode ser anterior ao início do vínculo.");
        }
        this.dataFim = data;
        this.motivoEncerramento = motivo;
        this.responsavelFinanceiro = false;
    }

    public void setResponsavelFinanceiro(boolean responsavelFinanceiro) {
        this.responsavelFinanceiro = responsavelFinanceiro;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public Unidade getUnidade() {
        return unidade;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public boolean isResponsavelFinanceiro() {
        return responsavelFinanceiro;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public String getMotivoEncerramento() {
        return motivoEncerramento;
    }
}
