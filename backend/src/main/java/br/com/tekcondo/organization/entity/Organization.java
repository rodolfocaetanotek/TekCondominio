package br.com.tekcondo.organization.entity;

import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Administradora de condomínios: unidade de isolamento (tenant) da plataforma. */
@Entity
@Table(name = "organization")
public class Organization extends AuditableEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "cnpj", nullable = false)
    private String cnpj;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Organization() {
    }

    public Organization(String name, String cnpj, String email, String phone) {
        this.name = name;
        this.cnpj = cnpj;
        this.email = email;
        this.phone = phone;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }
}
