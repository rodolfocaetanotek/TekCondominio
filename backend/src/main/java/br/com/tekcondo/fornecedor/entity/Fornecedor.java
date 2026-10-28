package br.com.tekcondo.fornecedor.entity;

import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Fornecedor da administradora (atende um ou mais condomínios). */
@Entity
@Table(name = "supplier")
public class Fornecedor extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "document")
    private String documento;

    @Column(name = "category")
    private String categoria;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String telefone;

    @Column(name = "contact_name")
    private String nomeContato;

    @Column(name = "notes")
    private String observacoes;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected Fornecedor() {
    }

    public Fornecedor(UUID idAdministradora) {
        this.idAdministradora = idAdministradora;
    }

    public void atualizar(String nome, String documento, String categoria, String email, String telefone, String nomeContato,
                          String observacoes) {
        this.nome = nome.trim();
        this.documento = documento;
        this.categoria = categoria;
        this.email = email;
        this.telefone = telefone;
        this.nomeContato = nomeContato;
        this.observacoes = observacoes;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public String getNome() {
        return nome;
    }

    public String getDocumento() {
        return documento;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getNomeContato() {
        return nomeContato;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
