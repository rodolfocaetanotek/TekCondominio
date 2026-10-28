package br.com.tekcondo.estrutura.entity;

import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Bloco ou torre do condomínio. */
@Entity
@Table(name = "building")
public class Bloco extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "description")
    private String descricao;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected Bloco() {
    }

    public Bloco(UUID idAdministradora, UUID idCondominio, String nome, String descricao) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        atualizar(nome, descricao);
    }

    public void atualizar(String nome, String descricao) {
        this.nome = nome.trim();
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.trim();
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
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

    public boolean isAtivo() {
        return ativo;
    }
}
