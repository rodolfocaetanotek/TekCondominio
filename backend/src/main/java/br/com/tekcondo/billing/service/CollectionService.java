package br.com.tekcondo.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.billing.dto.BillingDtos.AgreementRequest;
import br.com.tekcondo.billing.dto.BillingDtos.AgreementResponse;
import br.com.tekcondo.billing.dto.BillingDtos.DebtLine;
import br.com.tekcondo.billing.dto.BillingDtos.DelinquencyReport;
import br.com.tekcondo.billing.dto.BillingDtos.DelinquencyRow;
import br.com.tekcondo.billing.dto.BillingDtos.NoteRequest;
import br.com.tekcondo.billing.dto.BillingDtos.NoteResponse;
import br.com.tekcondo.billing.dto.BillingDtos.UnitDebt;
import br.com.tekcondo.billing.entity.Agreement;
import br.com.tekcondo.billing.entity.CollectionNote;
import br.com.tekcondo.billing.repository.AgreementRepository;
import br.com.tekcondo.billing.repository.CollectionNoteRepository;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.ChartAccountRepository;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.finance.service.EntryService;
import br.com.tekcondo.finance.service.LateFeeCalculator;
import br.com.tekcondo.finance.service.PeriodLock;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.people.repository.OccupancyRepository;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.UnitRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inadimplência, demonstrativo de débitos, histórico de cobrança e acordos de parcelamento.
 * Inadimplência = títulos a receber de unidades, em aberto e vencidos na data de referência.
 */
@Service
public class CollectionService {

    private static final String AGREEMENT_ACCOUNT_CODE = "1.07";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FinancialEntryRepository entries;
    private final AgreementRepository agreements;
    private final CollectionNoteRepository notes;
    private final ChartAccountRepository chartAccounts;
    private final UnitRepository units;
    private final OccupancyRepository occupancies;
    private final EntryService entryService;
    private final PeriodLock periodLock;
    private final CondominiumAccess access;
    private final AuditService auditService;
    private final Clock clock;

    public CollectionService(FinancialEntryRepository entries, AgreementRepository agreements, CollectionNoteRepository notes,
                             ChartAccountRepository chartAccounts, UnitRepository units, OccupancyRepository occupancies,
                             EntryService entryService, PeriodLock periodLock, CondominiumAccess access, AuditService auditService,
                             Clock clock) {
        this.entries = entries;
        this.agreements = agreements;
        this.notes = notes;
        this.chartAccounts = chartAccounts;
        this.units = units;
        this.occupancies = occupancies;
        this.entryService = entryService;
        this.periodLock = periodLock;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- inadimplência

    @Transactional(readOnly = true)
    public DelinquencyReport delinquency(UUID condominiumId, LocalDate asOfParam) {
        access.require(condominiumId, Permission.BILLING_VIEW);
        LocalDate asOf = asOfParam == null ? LocalDate.now(clock) : asOfParam;
        List<FinancialEntry> overdue = entries.findAll(overdueReceivables(condominiumId, asOf));
        Map<UUID, List<FinancialEntry>> byUnit = overdue.stream().collect(Collectors.groupingBy(e -> e.getUnit().getId(),
                LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Person> responsible = responsibles(byUnit.keySet());
        List<DelinquencyRow> rows = new ArrayList<>();
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalCharges = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<FinancialEntry>> e : byUnit.entrySet()) {
            BigDecimal principal = BigDecimal.ZERO;
            BigDecimal charges = BigDecimal.ZERO;
            LocalDate oldest = null;
            for (FinancialEntry entry : e.getValue()) {
                LateFeeCalculator.Quote q = quote(entry, asOf);
                principal = principal.add(q.principal());
                charges = charges.add(q.fine()).add(q.interest());
                oldest = oldest == null || entry.getDueDate().isBefore(oldest) ? entry.getDueDate() : oldest;
            }
            Unit unit = e.getValue().getFirst().getUnit();
            Person p = responsible.get(e.getKey());
            LocalDate lastContact = notes.findByCondominiumIdAndUnitIdOrderByContactDateDescCreatedAtDesc(condominiumId, e.getKey())
                    .stream().findFirst().map(CollectionNote::getContactDate).orElse(null);
            rows.add(new DelinquencyRow(e.getKey(), unit.label(), p == null ? null : p.getName(), p == null ? null : p.getEmail(),
                    p == null ? null : p.getPhone(), e.getValue().size(), principal, charges, principal.add(charges), oldest,
                    java.time.temporal.ChronoUnit.DAYS.between(oldest, asOf), lastContact));
            totalPrincipal = totalPrincipal.add(principal);
            totalCharges = totalCharges.add(charges);
        }
        rows.sort(Comparator.comparing(DelinquencyRow::total).reversed());
        BigDecimal issued = entries.findAll(issuedReceivables(condominiumId, asOf.minusMonths(12), asOf)).stream()
                .map(FinancialEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = issued.signum() == 0 ? BigDecimal.ZERO
                : totalPrincipal.multiply(HUNDRED).divide(issued, 2, RoundingMode.HALF_UP);
        return new DelinquencyReport(asOf, totalPrincipal, totalCharges, totalPrincipal.add(totalCharges), issued, rate,
                rows.size(), units.countByCondominiumIdAndActiveTrue(condominiumId), rows);
    }

    /** Demonstrativo de débitos da unidade, com valores atualizados (multa e juros) até a data. */
    @Transactional(readOnly = true)
    public UnitDebt unitDebt(UUID condominiumId, UUID unitId, LocalDate asOfParam) {
        access.require(condominiumId, Permission.BILLING_VIEW);
        Unit unit = units.findByIdAndCondominiumId(unitId, condominiumId).orElseThrow(() -> ApiException.notFound("Unidade"));
        return buildUnitDebt(condominiumId, unit, asOfParam == null ? LocalDate.now(clock) : asOfParam);
    }

    public UnitDebt buildUnitDebt(UUID condominiumId, Unit unit, LocalDate asOf) {
        List<DebtLine> lines = new ArrayList<>();
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        for (FinancialEntry e : entries.findReceivablesOfUnit(condominiumId, unit.getId())) {
            if (e.getStatus() == FinancialEntry.Status.CANCELLED) {
                continue;
            }
            boolean open = e.getStatus() == FinancialEntry.Status.OPEN || e.getStatus() == FinancialEntry.Status.PARTIAL;
            LateFeeCalculator.Quote q = open ? quote(e, asOf)
                    : new LateFeeCalculator.Quote(BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            lines.add(new DebtLine(e.getId(), e.getDescription(), e.getCompetence(), e.getDueDate(), e.getAmount(),
                    e.getPaidAmount(), open ? e.remaining() : BigDecimal.ZERO, e.getStatus(), q.daysLate(), q.fine(),
                    q.interest(), q.total()));
            if (open) {
                principal = principal.add(q.principal());
                charges = charges.add(q.fine()).add(q.interest());
            }
        }
        Person p = responsibles(Set.of(unit.getId())).get(unit.getId());
        List<NoteResponse> history = notes.findByCondominiumIdAndUnitIdOrderByContactDateDescCreatedAtDesc(condominiumId, unit.getId())
                .stream().map(NoteResponse::from).toList();
        List<AgreementResponse> unitAgreements = agreements.findByCondominiumIdOrderByCreatedAtDesc(condominiumId).stream()
                .filter(a -> a.getUnitId().equals(unit.getId())).map(a -> toResponse(a, unit)).toList();
        return new UnitDebt(unit.getId(), unit.label(), p == null ? null : p.getName(), asOf, principal, charges,
                principal.add(charges), lines, history, unitAgreements);
    }

    @Transactional
    public NoteResponse addNote(UUID condominiumId, UUID unitId, NoteRequest request) {
        Condominium condominium = access.require(condominiumId, Permission.BILLING_EDIT);
        units.findByIdAndCondominiumId(unitId, condominiumId).orElseThrow(() -> ApiException.notFound("Unidade"));
        if (request.entryId() != null) {
            FinancialEntry e = entryService.load(condominiumId, request.entryId());
            if (e.getUnit() == null || !e.getUnit().getId().equals(unitId)) {
                throw ApiException.businessRule("ENTRY_NOT_OF_UNIT", "A cobrança informada não pertence a esta unidade.");
            }
        }
        var user = access.user();
        CollectionNote note = notes.save(new CollectionNote(condominium.getOrganizationId(), condominiumId, unitId,
                request.entryId(), request.contactType(), request.contactDate(), request.note().trim(), user.userId(), user.name()));
        auditService.record(AuditAction.COLLECTION_NOTE_ADDED, "Unit", unitId, Map.of("type", request.contactType().name()));
        return NoteResponse.from(note);
    }

    // ---------------------------------------------------------------- acordos

    @Transactional(readOnly = true)
    public List<AgreementResponse> agreements(UUID condominiumId) {
        access.require(condominiumId, Permission.BILLING_VIEW);
        return agreements.findByCondominiumIdOrderByCreatedAtDesc(condominiumId).stream()
                .map(a -> toResponse(a, units.findById(a.getUnitId()).orElse(null))).toList();
    }

    @Transactional
    public AgreementResponse createAgreement(UUID condominiumId, AgreementRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permission.BILLING_EDIT);
        Unit unit = units.findByIdAndCondominiumId(request.unitId(), condominiumId).orElseThrow(() -> ApiException.notFound("Unidade"));
        LocalDate today = LocalDate.now(clock);
        if (request.firstDueDate().isBefore(today)) {
            throw ApiException.businessRule("FIRST_DUE_IN_PAST", "O vencimento da 1ª parcela não pode estar no passado.");
        }
        List<FinancialEntry> originals = new ArrayList<>();
        for (UUID id : new HashSet<>(request.entryIds())) {
            FinancialEntry e = entryService.load(condominiumId, id);
            if (e.getKind() != FinancialEntry.Kind.INCOME || e.getUnit() == null || !e.getUnit().getId().equals(unit.getId())) {
                throw ApiException.businessRule("ENTRY_NOT_OF_UNIT", "Selecione apenas cobranças desta unidade.");
            }
            if (e.getStatus() != FinancialEntry.Status.OPEN && e.getStatus() != FinancialEntry.Status.PARTIAL) {
                throw ApiException.businessRule("ENTRY_NOT_OPEN", "A cobrança \"" + e.getDescription() + "\" não está em aberto.");
            }
            originals.add(e);
        }
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        for (FinancialEntry e : originals) {
            LateFeeCalculator.Quote q = quote(e, today);
            principal = principal.add(q.principal());
            if (request.includeCharges()) {
                charges = charges.add(q.fine()).add(q.interest());
            }
        }
        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO : request.discount();
        Person responsible = responsibles(Set.of(unit.getId())).get(unit.getId());
        UUID by = access.user().userId();
        Agreement agreement = agreements.save(new Agreement(condominium.getOrganizationId(), condominiumId, unit.getId(),
                responsible == null ? null : responsible.getId(), principal, charges, discount, request.installments(),
                request.firstDueDate(), blank(request.notes()), by));
        originals.forEach(e -> {
            e.renegotiate(agreement.getId(), by);
            agreements.linkOriginal(agreement.getId(), e.getId());
        });

        ChartAccount account = chartAccounts.findSystemByCode(AGREEMENT_ACCOUNT_CODE).orElseThrow();
        BigDecimal installment = agreement.getTotalAmount().divide(BigDecimal.valueOf(request.installments()), 2, RoundingMode.DOWN);
        BigDecimal last = agreement.getTotalAmount().subtract(installment.multiply(BigDecimal.valueOf(request.installments() - 1L)));
        String ref = agreement.getId().toString().substring(0, 8).toUpperCase();
        for (int i = 0; i < request.installments(); i++) {
            FinancialEntry e = new FinancialEntry(condominium.getOrganizationId(), condominiumId, FinancialEntry.Kind.INCOME,
                    FinancialEntry.Origin.AGREEMENT, by);
            LocalDate due = request.firstDueDate().plusMonths(i);
            e.details("Acordo " + ref + " — parcela " + (i + 1) + "/" + request.installments(), account, null, null, unit,
                    responsible, i == request.installments() - 1 ? last : installment, due, due, null, null, by);
            e.lateFees(condominium.getFinePercent(), condominium.getInterestMonthlyPercent());
            e.linkAgreement(agreement.getId());
            e.recurrence(agreement.getId(), i + 1, request.installments());
            entries.save(e);
        }
        entries.flush();
        auditService.record(AuditAction.AGREEMENT_CREATED, "Agreement", agreement.getId(), Map.of("unit", unit.label(),
                "original", principal.toPlainString(), "charges", charges.toPlainString(), "discount", discount.toPlainString(),
                "total", agreement.getTotalAmount().toPlainString(), "installments", request.installments()));
        return toResponse(agreement, unit);
    }

    /** Cancela acordo sem parcelas pagas: as parcelas são canceladas e os débitos originais voltam a ficar em aberto. */
    @Transactional
    public AgreementResponse cancelAgreement(UUID condominiumId, UUID id, String reason) {
        access.require(condominiumId, Permission.BILLING_EDIT);
        Agreement agreement = agreements.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.notFound("Acordo"));
        List<FinancialEntry> parcels = entries.findByAgreementId(id);
        if (parcels.stream().anyMatch(e -> e.getPaidAmount().signum() > 0)) {
            throw ApiException.businessRule("AGREEMENT_HAS_PAYMENTS",
                    "O acordo possui parcelas pagas e não pode ser cancelado. Registre o descumprimento no histórico de cobrança.");
        }
        periodLock.ensureOpen(condominiumId, parcels.stream().map(FinancialEntry::getCompetence).toArray(LocalDate[]::new));
        UUID by = access.user().userId();
        Instant now = Instant.now(clock);
        agreement.cancel(reason.trim(), by, now);
        parcels.forEach(e -> e.cancel("Acordo cancelado: " + reason.trim(), by, now));
        agreements.originalEntryIds(id).forEach(entryId -> entries.findById(entryId).ifPresent(e -> e.restoreFromAgreement(by)));
        entries.flush();
        auditService.record(AuditAction.AGREEMENT_CANCELLED, "Agreement", id, Map.of("reason", reason.trim()));
        return toResponse(agreement, units.findById(agreement.getUnitId()).orElse(null));
    }

    // ---------------------------------------------------------------- apoio

    private LateFeeCalculator.Quote quote(FinancialEntry e, LocalDate asOf) {
        return LateFeeCalculator.quote(e.remaining(), e.getDueDate(), asOf, e.getFinePercent(), e.getInterestMonthlyPercent());
    }

    private static Specification<FinancialEntry> overdueReceivables(UUID condominiumId, LocalDate asOf) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("unit").fetch("building", JoinType.LEFT);
            }
            return cb.and(cb.equal(root.get("condominiumId"), condominiumId),
                    cb.equal(root.get("kind"), FinancialEntry.Kind.INCOME),
                    cb.isNotNull(root.get("unit")),
                    root.get("status").in(FinancialEntry.Status.OPEN, FinancialEntry.Status.PARTIAL),
                    cb.lessThan(root.get("dueDate"), asOf));
        };
    }

    private static Specification<FinancialEntry> issuedReceivables(UUID condominiumId, LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("condominiumId"), condominiumId),
                cb.equal(root.get("kind"), FinancialEntry.Kind.INCOME),
                cb.isNotNull(root.get("unit")),
                cb.notEqual(root.get("status"), FinancialEntry.Status.CANCELLED),
                cb.notEqual(root.get("origin"), FinancialEntry.Origin.AGREEMENT),
                cb.between(root.get("dueDate"), from, to));
    }

    private Map<UUID, Person> responsibles(Set<UUID> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Person> result = new java.util.HashMap<>();
        occupancies.findCurrentByUnits(unitIds).stream()
                .collect(Collectors.groupingBy(o -> o.getUnit().getId()))
                .forEach((unitId, occ) -> occ.stream().filter(Occupancy::isFinancialResponsible).findFirst()
                        .or(() -> occ.stream().filter(o -> o.getRole() == Occupancy.Role.OWNER).findFirst())
                        .ifPresent(o -> result.put(unitId, o.getPerson())));
        return result;
    }

    private AgreementResponse toResponse(Agreement a, Unit unit) {
        LocalDate today = LocalDate.now(clock);
        List<FinancialEntry> parcels = entries.findByAgreementId(a.getId());
        long paid = parcels.stream().filter(e -> e.getStatus() == FinancialEntry.Status.PAID).count();
        Agreement.Status status = a.getStatus() == Agreement.Status.ACTIVE && !parcels.isEmpty() && paid == parcels.size()
                ? Agreement.Status.COMPLETED : a.getStatus();
        return new AgreementResponse(a.getId(), a.getUnitId(), unit == null ? null : unit.label(), a.getOriginalAmount(),
                a.getChargesAmount(), a.getDiscount(), a.getTotalAmount(), a.getInstallments(), a.getFirstDueDate(), status,
                paid, a.getNotes(), a.getCancelReason(), a.getCreatedAt(),
                parcels.stream().sorted(Comparator.comparing(FinancialEntry::getDueDate))
                        .map(e -> entryService.summary(e, today)).toList());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
