package br.com.tekcondo.financeiro.entity;

import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cost_center")
public class CentroCusto extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID idCondominio;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected CentroCusto() {
    }

    public CentroCusto(UUID idAdministradora, UUID idCondominio, String nome) {
        this.idAdministradora = idAdministradora;
        this.idCondominio = idCondominio;
        this.nome = nome.trim();
    }

    public void renomear(String nome) {
        this.nome = nome.trim();
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public UUID getIdCondominio() {
        return idCondominio;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
