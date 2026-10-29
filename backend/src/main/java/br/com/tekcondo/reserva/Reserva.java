package br.com.tekcondo.reserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Reserva de área comum por uma unidade. */
@Entity
@Table(name = "reservation")
public class Reserva extends AuditableEntity {

    public enum Situacao {
        PENDING, CONFIRMED, REJECTED, CANCELLED;

        public boolean isAtiva() {
            return this == PENDING || this == CONFIRMED;
        }
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "area_id", nullable = false, updatable = false)
    private UUID idArea;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID idUnidade;

    @Column(name = "reservation_date", nullable = false, updatable = false)
    private LocalDate data;

    @Convert(converter = MinutoDoDia.class)
    @Column(name = "start_minute", nullable = false, updatable = false)
    private LocalTime horaInicio;

    @Convert(converter = MinutoDoDia.class)
    @Column(name = "end_minute", nullable = false, updatable = false)
    private LocalTime horaFim;

    @Column(name = "guests")
    private Integer convidados;

    @Column(name = "notes")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao;

    @Column(name = "fee", nullable = false, updatable = false)
    private BigDecimal taxa;

    @Column(name = "entry_id")
    private UUID idLancamento;

    @Column(name = "requested_by", updatable = false)
    private UUID solicitadoPor;

    @Column(name = "requester_name", nullable = false, updatable = false)
    private String nomeSolicitante;

    @Column(name = "by_resident", nullable = false, updatable = false)
    private boolean peloMorador;

    @Column(name = "decision_note")
    private String observacaoDecisao;

    @Column(name = "decided_by")
    private UUID decididoPor;

    @Column(name = "decided_at")
    private Instant decididoEm;

    @Column(name = "cancel_reason")
    private String motivoCancelamento;

    @Column(name = "cancelled_at")
    private Instant canceladoEm;

    protected Reserva() {
    }

    public Reserva(AreaComum area, UUID idUnidade, LocalDate data, LocalTime horaInicio, LocalTime horaFim, Integer convidados,
                       String observacoes, UUID solicitadoPor, String nomeSolicitante, boolean peloMorador) {
        this.idAdministradora = area.getIdAdministradora();
        this.idCondominio = area.getIdCondominio();
        this.idArea = area.getId();
        this.idUnidade = idUnidade;
        this.data = data;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.convidados = convidados;
        this.observacoes = observacoes;
        this.taxa = area.getTaxa();
        this.solicitadoPor = solicitadoPor;
        this.nomeSolicitante = nomeSolicitante;
        this.peloMorador = peloMorador;
        this.situacao = area.isExigeAprovacao() && peloMorador ? Situacao.PENDING : Situacao.CONFIRMED;
    }

    public void decidir(boolean aprovada, String observacao, UUID por, Instant agora) {
        if (situacao != Situacao.PENDING) {
            throw ApiException.regraNegocio("RESERVATION_NOT_PENDING", "A reserva não está aguardando aprovação.");
        }
        situacao = aprovada ? Situacao.CONFIRMED : Situacao.REJECTED;
        observacaoDecisao = observacao;
        decididoPor = por;
        decididoEm = agora;
    }

    public void cancelar(String motivo, Instant agora) {
        if (!situacao.isAtiva()) {
            throw ApiException.regraNegocio("RESERVATION_NOT_ACTIVE", "A reserva já está cancelada ou foi recusada.");
        }
        situacao = Situacao.CANCELLED;
        motivoCancelamento = motivo;
        canceladoEm = agora;
    }

    public void vincularCobranca(UUID idLancamento) {
        this.idLancamento = idLancamento;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdArea() {
        return idArea;
    }

    public UUID getIdUnidade() {
        return idUnidade;
    }

    public LocalDate getData() {
        return data;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFim() {
        return horaFim;
    }

    public Integer getConvidados() {
        return convidados;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public BigDecimal getTaxa() {
        return taxa;
    }

    public UUID getIdLancamento() {
        return idLancamento;
    }

    public UUID getSolicitadoPor() {
        return solicitadoPor;
    }

    public String getNomeSolicitante() {
        return nomeSolicitante;
    }

    public boolean isPeloMorador() {
        return peloMorador;
    }

    public String getObservacaoDecisao() {
        return observacaoDecisao;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }
}
