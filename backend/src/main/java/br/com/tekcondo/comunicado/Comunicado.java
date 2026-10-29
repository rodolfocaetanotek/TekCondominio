package br.com.tekcondo.comunicado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Comunicado do condomínio, para todos, um bloco ou uma unidade. */
@Entity
@Table(name = "announcement")
public class Comunicado {

    public enum PublicoAlvo {
        ALL, BUILDING, UNIT
    }

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "title", nullable = false, updatable = false)
    private String titulo;

    @Column(name = "body", nullable = false, updatable = false)
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, updatable = false)
    private PublicoAlvo publicoAlvo;

    @Column(name = "building_id", updatable = false)
    private UUID idBloco;

    @Column(name = "unit_id", updatable = false)
    private UUID idUnidade;

    @Column(name = "pinned", nullable = false)
    private boolean fixado;

    @Column(name = "send_email", nullable = false, updatable = false)
    private boolean enviarEmail;

    @Column(name = "expires_on")
    private LocalDate dataExpiracao;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publicadoEm;

    @Column(name = "archived_at")
    private Instant arquivadoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "created_by_name", updatable = false)
    private String nomeCriadoPor;

    protected Comunicado() {
    }

    public Comunicado(UUID idAdministradora, UUID idCondominio, String titulo, String texto, PublicoAlvo publicoAlvo,
                        UUID idBloco, UUID idUnidade, boolean fixado, boolean enviarEmail, LocalDate dataExpiracao,
                        Instant publicadoEm, UUID criadoPor, String nomeCriadoPor) {
        if (publicoAlvo == PublicoAlvo.BUILDING && idBloco == null || publicoAlvo == PublicoAlvo.UNIT && idUnidade == null) {
            throw ApiException.requisicaoInvalida("AUDIENCE_TARGET_REQUIRED", "Informe o bloco ou a unidade de destino.");
        }
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.titulo = titulo;
        this.texto = texto;
        this.publicoAlvo = publicoAlvo;
        this.idBloco = publicoAlvo == PublicoAlvo.BUILDING ? idBloco : null;
        this.idUnidade = publicoAlvo == PublicoAlvo.UNIT ? idUnidade : null;
        this.fixado = fixado;
        this.enviarEmail = enviarEmail;
        this.dataExpiracao = dataExpiracao;
        this.publicadoEm = publicadoEm;
        this.criadoPor = criadoPor;
        this.nomeCriadoPor = nomeCriadoPor;
    }

    public void arquivar(Instant agora) {
        if (arquivadoEm != null) {
            throw ApiException.regraNegocio("ANNOUNCEMENT_ARCHIVED", "O comunicado já está arquivado.");
        }
        arquivadoEm = agora;
        fixado = false;
    }

    public boolean isVisivel(LocalDate hoje) {
        return arquivadoEm == null && (dataExpiracao == null || !dataExpiracao.isBefore(hoje));
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getTexto() {
        return texto;
    }

    public PublicoAlvo getPublicoAlvo() {
        return publicoAlvo;
    }

    public UUID getIdBloco() {
        return idBloco;
    }

    public UUID getIdUnidade() {
        return idUnidade;
    }

    public boolean isFixado() {
        return fixado;
    }

    public boolean isEnviarEmail() {
        return enviarEmail;
    }

    public LocalDate getDataExpiracao() {
        return dataExpiracao;
    }

    public Instant getPublicadoEm() {
        return publicadoEm;
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }

    public String getNomeCriadoPor() {
        return nomeCriadoPor;
    }
}
