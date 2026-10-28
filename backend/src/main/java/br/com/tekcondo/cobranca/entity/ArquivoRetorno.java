package br.com.tekcondo.cobranca.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Arquivo de retorno de cobrança processado. Somente inclusão. */
@Entity
@Table(name = "bank_return_file")
public class ArquivoRetorno {

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

    @Column(name = "bank_code", nullable = false, updatable = false)
    private String codigoBanco;

    @Column(name = "generated_on", updatable = false)
    private LocalDate dataGeracao;

    @Column(name = "items_total", nullable = false)
    private int totalItens;

    @Column(name = "settled", nullable = false)
    private int baixados;

    @Column(name = "pending", nullable = false)
    private int pendentes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "created_by_name", updatable = false)
    private String nomeCriadoPor;

    protected ArquivoRetorno() {
    }

    public ArquivoRetorno(UUID idAdministradora, UUID idCondominio, UUID idContaBancaria, String nomeArquivo, String hashArquivo,
                          String codigoBanco, LocalDate dataGeracao, int totalItens, UUID criadoPor, String nomeCriadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.idContaBancaria = idContaBancaria;
        this.nomeArquivo = nomeArquivo;
        this.hashArquivo = hashArquivo;
        this.codigoBanco = codigoBanco;
        this.dataGeracao = dataGeracao;
        this.totalItens = totalItens;
        this.criadoPor = criadoPor;
        this.nomeCriadoPor = nomeCriadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public void registrarResultado(int baixados, int pendentes) {
        this.baixados = baixados;
        this.pendentes = pendentes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public UUID getIdContaBancaria() {
        return idContaBancaria;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getCodigoBanco() {
        return codigoBanco;
    }

    public LocalDate getDataGeracao() {
        return dataGeracao;
    }

    public int getTotalItens() {
        return totalItens;
    }

    public int getBaixados() {
        return baixados;
    }

    public int getPendentes() {
        return pendentes;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getNomeCriadoPor() {
        return nomeCriadoPor;
    }
}
