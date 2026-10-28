package br.com.tekcondo.people.entity;

import java.util.UUID;

import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Pessoa física ou jurídica. Cadastro único por administradora; os papéis ficam nas ocupações. */
@Entity
@Table(name = "person")
public class Person extends AuditableEntity {

    public enum Kind {
        INDIVIDUAL, COMPANY
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Kind kind;

    @Column(name = "document")
    private String document;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "secondary_phone")
    private String secondaryPhone;

    @Column(name = "notes")
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Person() {
    }

    public Person(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public void update(String name, Kind kind, String document, String email, String phone, String secondaryPhone,
                       String notes) {
        this.name = name.trim();
        this.kind = kind;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.secondaryPhone = secondaryPhone;
        this.notes = notes;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getDocument() {
        return document;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getSecondaryPhone() {
        return secondaryPhone;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
