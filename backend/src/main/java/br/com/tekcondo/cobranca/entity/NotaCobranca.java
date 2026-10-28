package br.com.tekcondo.cobranca.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Registro de contato de cobrança com a unidade (histórico de cobrança). Somente inclusão. */
@Entity
@Table(name = "collection_note")
public class NotaCobranca {

    public enum TipoContato {
        PHONE, EMAIL, WHATSAPP, LETTER, IN_PERSON, LEGAL, OTHER
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID idUnidade;

    @Column(name = "entry_id", updatable = false)
    private UUID idLancamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, updatable = false)
    private TipoContato tipoContato;

    @Column(name = "contact_date", nullable = false, updatable = false)
    private LocalDate dataContato;

    @Column(name = "note", nullable = false, updatable = false)
    private String observacao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "created_by_name", updatable = false)
    private String nomeCriadoPor;

    protected NotaCobranca() {
    }

    public NotaCobranca(UUID idAdministradora, UUID idCondominio, UUID idUnidade, UUID idLancamento, TipoContato tipoContato,
                        LocalDate dataContato, String observacao, UUID criadoPor, String nomeCriadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.idUnidade = idUnidade;
        this.idLancamento = idLancamento;
        this.tipoContato = tipoContato;
        this.dataContato = dataContato;
        this.observacao = observacao;
        this.criadoPor = criadoPor;
        this.nomeCriadoPor = nomeCriadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdLancamento() {
        return idLancamento;
    }

    public TipoContato getTipoContato() {
        return tipoContato;
    }

    public LocalDate getDataContato() {
        return dataContato;
    }

    public String getObservacao() {
        return observacao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getNomeCriadoPor() {
        return nomeCriadoPor;
    }
}
