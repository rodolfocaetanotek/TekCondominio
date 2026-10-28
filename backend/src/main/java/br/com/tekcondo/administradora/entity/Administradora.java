package br.com.tekcondo.administradora.entity;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Administradora de condomínios: unidade de isolamento (tenant) da plataforma. */
@Entity
@Table(name = "organization")
public class Administradora extends AuditableEntity {

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "cnpj", nullable = false)
    private String cnpj;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String telefone;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected Administradora() {
    }

    public Administradora(String nome, String cnpj, String email, String telefone) {
        this.nome = nome;
        this.cnpj = cnpj;
        this.email = email;
        this.telefone = telefone;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public String getNome() {
        return nome;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
