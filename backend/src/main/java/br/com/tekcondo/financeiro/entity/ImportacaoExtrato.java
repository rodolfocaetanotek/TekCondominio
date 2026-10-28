package br.com.tekcondo.financeiro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Arquivo de extrato importado (OFX). Somente inclusão. */
@Entity
@Table(name = "bank_statement_import")
public class ImportacaoExtrato {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID idContaBancaria;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String nomeArquivo;

    @Column(name = "file_hash", nullable = false, updatable = false)
    private String hashArquivo;

    @Column(name = "period_start", updatable = false)
    private LocalDate inicioPeriodo;

    @Column(name = "period_end", updatable = false)
    private LocalDate fimPeriodo;

    @Column(name = "lines_total", nullable = false)
    private int totalLinhas;

    @Column(name = "lines_new", nullable = false)
    private int linhasNovas;

    @Column(name = "auto_matched", nullable = false)
    private int conciliadasAutomaticamente;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "created_by_name", updatable = false)
    private String nomeCriadoPor;

    protected ImportacaoExtrato() {
    }

    public ImportacaoExtrato(UUID idAdministradora, UUID idCondominio, UUID idContaBancaria, String nomeArquivo,
                             String hashArquivo, LocalDate inicioPeriodo, LocalDate fimPeriodo, int totalLinhas,
                             UUID criadoPor, String nomeCriadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.idContaBancaria = idContaBancaria;
        this.nomeArquivo = nomeArquivo;
        this.hashArquivo = hashArquivo;
        this.inicioPeriodo = inicioPeriodo;
        this.fimPeriodo = fimPeriodo;
        this.totalLinhas = totalLinhas;
        this.criadoPor = criadoPor;
        this.nomeCriadoPor = nomeCriadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public void registrarResultado(int linhasNovas, int conciliadasAutomaticamente) {
        this.linhasNovas = linhasNovas;
        this.conciliadasAutomaticamente = conciliadasAutomaticamente;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdContaBancaria() {
        return idContaBancaria;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public LocalDate getInicioPeriodo() {
        return inicioPeriodo;
    }

    public LocalDate getFimPeriodo() {
        return fimPeriodo;
    }

    public int getTotalLinhas() {
        return totalLinhas;
    }

    public int getLinhasNovas() {
        return linhasNovas;
    }

    public int getConciliadasAutomaticamente() {
        return conciliadasAutomaticamente;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getNomeCriadoPor() {
        return nomeCriadoPor;
    }
}
