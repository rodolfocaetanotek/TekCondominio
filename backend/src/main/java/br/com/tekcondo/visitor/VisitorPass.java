package br.com.tekcondo.visitor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Autorização de acesso (pré-cadastrada pelo morador ou registrada na chegada) e seu ciclo entrada → saída. */
@Entity
@Table(name = "visitor_pass")
public class VisitorPass extends AuditableEntity {

    public enum Kind {
        VISITOR, SERVICE_PROVIDER, DELIVERY
    }

    public enum Status {
        EXPECTED, INSIDE, LEFT, CANCELLED
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "document")
    private String document;

    @Column(name = "company")
    private String company;

    @Column(name = "vehicle_plate")
    private String vehiclePlate;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDate validUntil;

    @Column(name = "access_code")
    private String accessCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "by_resident", nullable = false, updatable = false)
    private boolean byResident;

    @Column(name = "authorized_by", updatable = false)
    private UUID authorizedBy;

    @Column(name = "authorized_by_name", nullable = false, updatable = false)
    private String authorizedByName;

    @Column(name = "entered_at")
    private Instant enteredAt;

    @Column(name = "entered_by_name")
    private String enteredByName;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "left_by_name")
    private String leftByName;

    @Column(name = "notes")
    private String notes;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    protected VisitorPass() {
    }

    public VisitorPass(UUID organizationId, UUID condominiumId, UUID unitId, Kind kind, String name, String document, String company,
                       String vehiclePlate, LocalDate validFrom, LocalDate validUntil, String notes, boolean byResident,
                       UUID authorizedBy, String authorizedByName) {
        this.organizationId = organizationId;
        this.condominiumId = condominiumId;
        this.unitId = unitId;
        this.kind = kind;
        this.name = name;
        this.document = document;
        this.company = company;
        this.vehiclePlate = vehiclePlate;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.notes = notes;
        this.byResident = byResident;
        this.authorizedBy = authorizedBy;
        this.authorizedByName = authorizedByName;
        this.status = Status.EXPECTED;
    }

    public void code(String accessCode) {
        this.accessCode = accessCode;
    }

    public void enter(LocalDate today, Instant now, String byName) {
        if (status != Status.EXPECTED) {
            throw ApiException.businessRule("VISITOR_NOT_EXPECTED", "Esta autorização já foi utilizada ou cancelada.");
        }
        if (today.isBefore(validFrom) || today.isAfter(validUntil)) {
            throw ApiException.businessRule("VISITOR_OUT_OF_PERIOD", "A autorização não é válida hoje (válida de "
                    + validFrom + " a " + validUntil + ").");
        }
        status = Status.INSIDE;
        enteredAt = now;
        enteredByName = byName;
        accessCode = null;
    }

    public void leave(Instant now, String byName) {
        if (status != Status.INSIDE) {
            throw ApiException.businessRule("VISITOR_NOT_INSIDE", "A entrada deste visitante não foi registrada.");
        }
        status = Status.LEFT;
        leftAt = now;
        leftByName = byName;
    }

    public void cancel() {
        if (status != Status.EXPECTED) {
            throw ApiException.businessRule("VISITOR_NOT_EXPECTED", "Somente autorizações ainda não utilizadas podem ser canceladas.");
        }
        status = Status.CANCELLED;
        accessCode = null;
    }

    /** LGPD: remove os dados pessoais mantendo o registro estatístico (data, unidade, tipo). */
    public void anonymize(Instant now) {
        name = "[anonimizado]";
        document = null;
        vehiclePlate = null;
        company = null;
        notes = null;
        accessCode = null;
        anonymizedAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getDocument() {
        return document;
    }

    public String getCompany() {
        return company;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isByResident() {
        return byResident;
    }

    public UUID getAuthorizedBy() {
        return authorizedBy;
    }

    public String getAuthorizedByName() {
        return authorizedByName;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public String getEnteredByName() {
        return enteredByName;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public String getNotes() {
        return notes;
    }
}
