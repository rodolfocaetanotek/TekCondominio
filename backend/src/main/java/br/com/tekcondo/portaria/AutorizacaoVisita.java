package br.com.tekcondo.portaria;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Autorização de acesso (pré-cadastrada pelo morador ou registrada na chegada) e seu ciclo entrada → saída. */
@Entity
@Table(name = "visitor_pass")
public class AutorizacaoVisita extends AuditableEntity {

    public enum Tipo {
        VISITOR, SERVICE_PROVIDER, DELIVERY
    }

    public enum Situacao {
        EXPECTED, INSIDE, LEFT, CANCELLED
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID idUnidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private Tipo tipo;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "document")
    private String documento;

    @Column(name = "company")
    private String empresa;

    @Column(name = "vehicle_plate")
    private String placaVeiculo;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validaDe;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDate validaAte;

    @Column(name = "access_code")
    private String codigoAcesso;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Situacao situacao;

    @Column(name = "by_resident", nullable = false, updatable = false)
    private boolean peloMorador;

    @Column(name = "authorized_by", updatable = false)
    private UUID autorizadoPor;

    @Column(name = "authorized_by_name", nullable = false, updatable = false)
    private String nomeAutorizadoPor;

    @Column(name = "entered_at")
    private Instant entradaEm;

    @Column(name = "entered_by_name")
    private String nomeEntradaPor;

    @Column(name = "left_at")
    private Instant saidaEm;

    @Column(name = "left_by_name")
    private String nomeSaidaPor;

    @Column(name = "notes")
    private String observacoes;

    @Column(name = "anonymized_at")
    private Instant anonimizadoEm;

    protected AutorizacaoVisita() {
    }

    public AutorizacaoVisita(UUID idAdministradora, UUID idCondominio, UUID idUnidade, Tipo tipo, String nome, String documento, String empresa,
                       String placaVeiculo, LocalDate validaDe, LocalDate validaAte, String observacoes, boolean peloMorador,
                       UUID autorizadoPor, String nomeAutorizadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.idUnidade = idUnidade;
        this.tipo = tipo;
        this.nome = nome;
        this.documento = documento;
        this.empresa = empresa;
        this.placaVeiculo = placaVeiculo;
        this.validaDe = validaDe;
        this.validaAte = validaAte;
        this.observacoes = observacoes;
        this.peloMorador = peloMorador;
        this.autorizadoPor = autorizadoPor;
        this.nomeAutorizadoPor = nomeAutorizadoPor;
        this.situacao = Situacao.EXPECTED;
    }

    public void definirCodigo(String codigoAcesso) {
        this.codigoAcesso = codigoAcesso;
    }

    public void registrarEntrada(LocalDate hoje, Instant agora, String nomeResponsavel) {
        if (situacao != Situacao.EXPECTED) {
            throw ApiException.regraNegocio("VISITOR_NOT_EXPECTED", "Esta autorização já foi utilizada ou cancelada.");
        }
        if (hoje.isBefore(validaDe) || hoje.isAfter(validaAte)) {
            throw ApiException.regraNegocio("VISITOR_OUT_OF_PERIOD", "A autorização não é válida hoje (válida de "
                    + validaDe + " a " + validaAte + ").");
        }
        situacao = Situacao.INSIDE;
        entradaEm = agora;
        nomeEntradaPor = nomeResponsavel;
        codigoAcesso = null;
    }

    public void registrarSaida(Instant agora, String nomeResponsavel) {
        if (situacao != Situacao.INSIDE) {
            throw ApiException.regraNegocio("VISITOR_NOT_INSIDE", "A entrada deste visitante não foi registrada.");
        }
        situacao = Situacao.LEFT;
        saidaEm = agora;
        nomeSaidaPor = nomeResponsavel;
    }

    public void cancelar() {
        if (situacao != Situacao.EXPECTED) {
            throw ApiException.regraNegocio("VISITOR_NOT_EXPECTED", "Somente autorizações ainda não utilizadas podem ser canceladas.");
        }
        situacao = Situacao.CANCELLED;
        codigoAcesso = null;
    }

    /** LGPD: remove os dados pessoais mantendo o registro estatístico (data, unidade, tipo). */
    public void anonimizar(Instant agora) {
        nome = "[anonimizado]";
        documento = null;
        placaVeiculo = null;
        empresa = null;
        observacoes = null;
        codigoAcesso = null;
        anonimizadoEm = agora;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public UUID getIdUnidade() {
        return idUnidade;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public String getDocumento() {
        return documento;
    }

    public String getEmpresa() {
        return empresa;
    }

    public String getPlacaVeiculo() {
        return placaVeiculo;
    }

    public LocalDate getValidaDe() {
        return validaDe;
    }

    public LocalDate getValidaAte() {
        return validaAte;
    }

    public String getCodigoAcesso() {
        return codigoAcesso;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public boolean isPeloMorador() {
        return peloMorador;
    }

    public UUID getAutorizadoPor() {
        return autorizadoPor;
    }

    public String getNomeAutorizadoPor() {
        return nomeAutorizadoPor;
    }

    public Instant getEntradaEm() {
        return entradaEm;
    }

    public String getNomeEntradaPor() {
        return nomeEntradaPor;
    }

    public Instant getSaidaEm() {
        return saidaEm;
    }

    public String getObservacoes() {
        return observacoes;
    }
}
