package br.com.tekcondo.reservation;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Área comum reservável e suas regras de uso. */
@Entity
@Table(name = "common_area")
public class CommonArea extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "rules")
    private String rules;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "fee", nullable = false)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "block_delinquent", nullable = false)
    private boolean blockDelinquent;

    @Convert(converter = MinuteOfDay.class)
    @Column(name = "open_minute", nullable = false)
    private LocalTime openTime;

    @Convert(converter = MinuteOfDay.class)
    @Column(name = "close_minute", nullable = false)
    private LocalTime closeTime;

    @Column(name = "max_duration_minutes", nullable = false)
    private int maxDurationMinutes;

    @Column(name = "min_advance_hours", nullable = false)
    private int minAdvanceHours;

    @Column(name = "max_advance_days", nullable = false)
    private int maxAdvanceDays;

    @Column(name = "max_per_unit_month")
    private Integer maxPerUnitMonth;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected CommonArea() {
    }

    public CommonArea(UUID organizationId, UUID condominiumId) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
    }

    public void update(String name, String description, String rules, Integer capacity, BigDecimal fee, boolean requiresApproval,
                       boolean blockDelinquent, LocalTime openTime, LocalTime closeTime, int maxDurationMinutes, int minAdvanceHours,
                       int maxAdvanceDays, Integer maxPerUnitMonth) {
        this.name = name;
        this.description = description;
        this.rules = rules;
        this.capacity = capacity;
        this.fee = fee == null ? BigDecimal.ZERO : fee;
        this.requiresApproval = requiresApproval;
        this.blockDelinquent = blockDelinquent;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.maxDurationMinutes = maxDurationMinutes;
        this.minAdvanceHours = minAdvanceHours;
        this.maxAdvanceDays = maxAdvanceDays;
        this.maxPerUnitMonth = maxPerUnitMonth;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getRules() {
        return rules;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public boolean isBlockDelinquent() {
        return blockDelinquent;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public int getMinAdvanceHours() {
        return minAdvanceHours;
    }

    public int getMaxAdvanceDays() {
        return maxAdvanceDays;
    }

    public Integer getMaxPerUnitMonth() {
        return maxPerUnitMonth;
    }

    public boolean isActive() {
        return active;
    }
}
