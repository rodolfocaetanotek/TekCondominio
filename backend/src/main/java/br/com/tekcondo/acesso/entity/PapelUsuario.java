package br.com.tekcondo.acesso.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Atribuição de papel. {@code idCondominio} nulo = vale para toda a organização. */
@Entity
@Table(name = "user_role")
public class PapelUsuario {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID idUsuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Papel papel;

    @Column(name = "organization_id", nullable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id")
    private UUID idCondominio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "created_by")
    private UUID criadoPor;

    protected PapelUsuario() {
    }

    public PapelUsuario(UUID idUsuario, Papel papel, UUID idAdministradora, UUID idCondominio, UUID criadoPor) {
        this.idUsuario = idUsuario;
        this.papel = papel;
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public Papel getPapel() {
        return papel;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
