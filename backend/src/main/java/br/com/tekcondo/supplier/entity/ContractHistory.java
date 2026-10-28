package br.com.tekcondo.supplier.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "contract_history")
public class ContractHistory {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "change", nullable = false, updatable = false)
    private String change;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    protected ContractHistory() {
    }

    public ContractHistory(UUID contractId, String change, String createdByName) {
        this.contractId = contractId;
        this.change = change.length() > 1000 ? change.substring(0, 1000) : change;
        this.createdByName = createdByName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getChange() {
        return change;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }
}
