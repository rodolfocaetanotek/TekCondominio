package br.com.tekcondo.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Valor orçado para uma conta em um mês do ano. */
@Entity
@Table(name = "budget_line")
public class BudgetLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(nullable = false, updatable = false)
    private int year;

    @Column(nullable = false, updatable = false)
    private int month;

    @Column(name = "chart_account_id", nullable = false, updatable = false)
    private UUID chartAccountId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected BudgetLine() {
    }

    public BudgetLine(UUID organizationId, UUID condominiumId, int year, int month, UUID chartAccountId) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.year = year;
        this.month = month;
        this.chartAccountId = chartAccountId;
    }

    public void setAmount(BigDecimal amount, UUID by) {
        this.amount = amount;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }

    public int getMonth() {
        return month;
    }

    public UUID getChartAccountId() {
        return chartAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
