package br.com.tekcondo.supplier.entity;

import java.util.UUID;

import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Fornecedor da administradora (atende um ou mais condomínios). */
@Entity
@Table(name = "supplier")
public class Supplier extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    private String document;

    private String category;

    private String email;

    private String phone;

    @Column(name = "contact_name")
    private String contactName;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected Supplier() {
    }

    public Supplier(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public void update(String name, String document, String category, String email, String phone, String contactName,
                       String notes) {
        this.name = name.trim();
        this.document = document;
        this.category = category;
        this.email = email;
        this.phone = phone;
        this.contactName = contactName;
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

    public String getDocument() {
        return document;
    }

    public String getCategory() {
        return category;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getContactName() {
        return contactName;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
