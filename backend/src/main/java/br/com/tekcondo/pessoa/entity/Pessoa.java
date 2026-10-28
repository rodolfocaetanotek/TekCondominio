package br.com.tekcondo.pessoa.entity;

import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Pessoa física ou jurídica. Cadastro único por administradora; os papéis ficam nos vínculos. */
@Entity
@Table(name = "person")
public class Pessoa extends AuditableEntity {

    /** Tipo de pessoa: física ({@code INDIVIDUAL}) ou jurídica ({@code COMPANY}). */
    public enum Tipo {
        INDIVIDUAL, COMPANY
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "name", nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Tipo tipo;

    @Column(name = "document")
    private String documento;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String telefone;

    @Column(name = "secondary_phone")
    private String telefoneSecundario;

    @Column(name = "notes")
    private String observacoes;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    protected Pessoa() {
    }

    public Pessoa(UUID idAdministradora) {
        this.idAdministradora = idAdministradora;
    }

    public void atualizar(String nome, Tipo tipo, String documento, String email, String telefone,
                          String telefoneSecundario, String observacoes) {
        this.nome = nome.trim();
        this.tipo = tipo;
        this.documento = documento;
        this.email = email;
        this.telefone = telefone;
        this.telefoneSecundario = telefoneSecundario;
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

    public Tipo getTipo() {
        return tipo;
    }

    public String getDocumento() {
        return documento;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getTelefoneSecundario() {
        return telefoneSecundario;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
