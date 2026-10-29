package br.com.tekcondo.documento.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Documento/anexo. Arquivamento lógico; o arquivo físico é preservado para histórico. */
@Entity
@Table(name = "document")
public class Documento {

    public enum Categoria {
        CONVENTION, INTERNAL_RULES, MINUTES, CONTRACT, INVOICE, RECEIPT, FINANCIAL_STATEMENT, UNIT, OCCURRENCE, OTHER
    }

    public enum Visibilidade {
        /** Apenas equipe autorizada (administradora, síndico, conselho). */
        INTERNAL,
        /** Também disponível aos moradores no portal. */
        RESIDENTS
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Categoria categoria;

    @Column(name = "title", nullable = false)
    private String titulo;

    @Column(name = "description")
    private String descricao;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String tipoConteudo;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long tamanhoBytes;

    @Column(name = "sha256", nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String chaveArmazenamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibilidade visibilidade;

    @Column(name = "entity_type", updatable = false)
    private String tipoEntidade;

    @Column(name = "entity_id", updatable = false)
    private UUID idEntidade;

    @Column(name = "unit_id")
    private UUID idUnidade;

    @Column(name = "archived_at")
    private Instant arquivadoEm;

    @Column(name = "archived_by")
    private UUID arquivadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "created_by_name", updatable = false)
    private String nomeCriadoPor;

    protected Documento() {
    }

    public Documento(UUID idAdministradora, UUID idCondominio, Categoria categoria, String titulo, String descricao,
                    String nomeArquivo, String tipoConteudo, long tamanhoBytes, String sha256, String chaveArmazenamento,
                    Visibilidade visibilidade, String tipoEntidade, UUID idEntidade, UUID idUnidade, UUID criadoPor, String nomeCriadoPor) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.categoria = categoria;
        this.titulo = titulo;
        this.descricao = descricao;
        this.nomeArquivo = nomeArquivo;
        this.tipoConteudo = tipoConteudo;
        this.tamanhoBytes = tamanhoBytes;
        this.sha256 = sha256;
        this.chaveArmazenamento = chaveArmazenamento;
        this.visibilidade = visibilidade;
        this.tipoEntidade = tipoEntidade;
        this.idEntidade = idEntidade;
        this.idUnidade = idUnidade;
        this.criadoPor = criadoPor;
        this.nomeCriadoPor = nomeCriadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public void arquivar(UUID por, Instant agora) {
        if (arquivadoEm != null) {
            throw ApiException.regraNegocio("DOCUMENT_ARCHIVED", "O documento já está arquivado.");
        }
        arquivadoEm = agora;
        arquivadoPor = por;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getTipoConteudo() {
        return tipoConteudo;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getChaveArmazenamento() {
        return chaveArmazenamento;
    }

    public Visibilidade getVisibilidade() {
        return visibilidade;
    }

    public String getTipoEntidade() {
        return tipoEntidade;
    }

    public UUID getIdEntidade() {
        return idEntidade;
    }

    public UUID getIdUnidade() {
        return idUnidade;
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getNomeCriadoPor() {
        return nomeCriadoPor;
    }
}
