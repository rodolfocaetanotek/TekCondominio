package br.com.tekcondo.organization.entity;

import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Administradora de condomínios: unidade de isolamento (tenant) da plataforma. */
@Entity
@Table(name = "organization")
public class Organization extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String cnpj;

    private String email;

    private String phone;

    @Column(nullable = false)
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
