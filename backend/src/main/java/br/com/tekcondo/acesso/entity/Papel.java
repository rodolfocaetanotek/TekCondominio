package br.com.tekcondo.acesso.entity;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Papel = conjunto de permissões. {@code idAdministradora} nulo indica papel padrão do sistema. */
@Entity
@Table(name = "role")
public class Papel extends AuditableEntity {

    public static final String ORG_ADMIN = "ORG_ADMIN";

    @Column(name = "organization_id")
    private UUID idAdministradora;

    @Column(name = "code", nullable = false)
    private String codigo;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "description")
    private String descricao;

    @Column(name = "system_role", nullable = false)
    private boolean papelSistema;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private EscopoPapel escopo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    @Enumerated(EnumType.STRING)
    private Set<Permissao> permissoes = EnumSet.noneOf(Permissao.class);

    protected Papel() {
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isPapelSistema() {
        return papelSistema;
    }

    public EscopoPapel getEscopo() {
        return escopo;
    }

    public Set<Permissao> getPermissoes() {
        return Set.copyOf(permissoes);
    }
}
