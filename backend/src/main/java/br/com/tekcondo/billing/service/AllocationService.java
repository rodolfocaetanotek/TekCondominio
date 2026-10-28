package br.com.tekcondo.billing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationLine;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationPreview;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationRequest;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationResponse;
import br.com.tekcondo.billing.entity.Allocation;
import br.com.tekcondo.billing.repository.AllocationRepository;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.ChartAccountRepository;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.finance.service.FinanceSetupService;
import br.com.tekcondo.finance.service.PeriodLock;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.people.repository.OccupancyRepository;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rateio e geração das cobranças mensais (um título a receber por unidade). */
@Service
public class AllocationService {

    private final AllocationRepository allocations;
    private final FinancialEntryRepository entries;
    private final ChartAccountRepository chartAccounts;
    private final UnitRepository units;
    private final OccupancyRepository occupancies;
    private final FinanceSetupService setup;
    private final PeriodLock periodLock;
    private final CondominiumAccess access;
    private final AuditService auditService;
    private final Clock clock;

    public AllocationService(AllocationRepository allocations, FinancialEntryRepository entries,
                             ChartAccountRepository chartAccounts, UnitRepository units, OccupancyRepository occupancies,
                             FinanceSetupService setup, PeriodLock periodLock, CondominiumAccess access, AuditService auditService,
                             Clock clock) {
        this.allocations = allocations;
        this.entries = entries;
        this.chartAccounts = chartAccounts;
        this.units = units;
        this.occupancies = occupancies;
        this.setup = setup;
        this.periodLock = periodLock;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    private record Plan(Condominium condominium, ChartAccount account, LocalDate competence, LocalDate dueDate,
                        List<Unit> units, Map<UUID, BigDecimal> shares, Map<UUID, Person> responsible, BigDecimal total) {
    }

    @Transactional(readOnly = true)
    public AllocationPreview preview(UUID condominiumId, AllocationRequest request) {
        return toPreview(plan(condominiumId, request));
    }

    @Transactional
    public AllocationResponse generate(UUID condominiumId, AllocationRequest request) {
        Plan plan = plan(condominiumId, request);
        periodLock.ensureOpen(condominiumId, plan.competence());
        UUID userId = access.user().userId();
        Allocation allocation = allocations.save(new Allocation(plan.condominium().getOrganizationId(), condominiumId,
                plan.competence(), request.description().trim(), plan.account().getId(), request.method(),
                request.method() == AllocationCalculator.Method.CONSUMPTION ? plan.total() : request.amount(),
                plan.dueDate(), plan.shares().size(), plan.total(), userId));
        for (Unit unit : plan.units()) {
            BigDecimal value = plan.shares().get(unit.getId());
            if (value == null || value.signum() <= 0) {
                continue;
            }
            FinancialEntry charge = new FinancialEntry(plan.condominium().getOrganizationId(), condominiumId,
                    FinancialEntry.Kind.INCOME, FinancialEntry.Origin.ALLOCATION, userId);
            charge.details(request.description().trim(), plan.account(), null, null, unit, plan.responsible().get(unit.getId()),
                    value, plan.dueDate(), plan.competence(), null, null, userId);
            charge.lateFees(plan.condominium().getFinePercent(), plan.condominium().getInterestMonthlyPercent());
            charge.linkAllocation(allocation.getId());
            entries.save(charge);
        }
        auditService.record(AuditAction.ALLOCATION_GENERATED, "Allocation", allocation.getId(), Map.of(
                "competence", plan.competence().toString(), "method", request.method().name(),
                "units", plan.shares().size(), "total", plan.total().toPlainString()));
        return toResponse(allocation, plan.account());
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> list(UUID condominiumId) {
        access.require(condominiumId, Permission.BILLING_VIEW);
        Map<UUID, ChartAccount> accounts = chartAccounts.findAvailable(access.organizationId()).stream()
                .collect(Collectors.toMap(ChartAccount::getId, a -> a));
        return allocations.findByCondominiumIdOrderByCompetenceDescCreatedAtDesc(condominiumId).stream()
                .map(a -> toResponse(a, accounts.get(a.getChartAccountId()))).toList();
    }

    /** Cancela o rateio e suas cobranças — somente se nenhuma tiver sido paga. */
    @Transactional
    public AllocationResponse cancel(UUID condominiumId, UUID id, String reason) {
        access.require(condominiumId, Permission.BILLING_EDIT);
        access.require(condominiumId, Permission.FINANCE_CANCEL);
        Allocation allocation = allocations.findByIdAndCondominiumId(id, condominiumId)
                .orElseThrow(() -> ApiException.notFound("Rateio"));
        periodLock.ensureOpen(condominiumId, allocation.getCompetence());
        List<FinancialEntry> charges = entries.findByAllocationId(id);
        if (charges.stream().anyMatch(e -> e.getPaidAmount().signum() > 0)) {
            throw ApiException.businessRule("ALLOCATION_HAS_PAYMENTS",
                    "Há cobranças deste rateio com pagamento. Estorne as baixas ou cancele as cobranças individualmente.");
        }
        UUID by = access.user().userId();
        Instant now = Instant.now(clock);
        charges.stream().filter(e -> e.getStatus() == FinancialEntry.Status.OPEN)
                .forEach(e -> e.cancel("Rateio cancelado: " + reason.trim(), by, now));
        allocation.cancel(reason.trim(), by, now);
        auditService.record(AuditAction.ALLOCATION_CANCELLED, "Allocation", id, Map.of("reason", reason.trim()));
        return toResponse(allocation, chartAccounts.findById(allocation.getChartAccountId()).orElse(null));
    }

    // ------------------------------------------------------------------

    private Plan plan(UUID condominiumId, AllocationRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permission.BILLING_EDIT);
        ChartAccount account = setup.analyticAccount(request.chartAccountId(), ChartAccount.Nature.INCOME);
        LocalDate competence = YearMonth.parse(request.competence()).atDay(1);
        if (allocations.existsGenerated(condominiumId, competence, account.getId())) {
            throw ApiException.conflict("ALLOCATION_EXISTS", "Já existe cobrança gerada para " + account.getName()
                    + " na competência " + YearMonth.from(competence) + ". Cancele a anterior para gerar novamente.");
        }
        if (request.method() != AllocationCalculator.Method.CONSUMPTION && request.amount() == null) {
            throw ApiException.badRequest("AMOUNT_REQUIRED", "Informe o valor a ratear.");
        }
        LocalDate dueDate = request.dueDate() != null ? request.dueDate()
                : competence.withDayOfMonth(condominium.getBillingDueDay());
        List<Unit> active = units.findActiveByCondominium(condominiumId).stream()
                .sorted(Comparator.comparing(Unit::label)).toList();
        List<AllocationCalculator.Share> shares = active.stream()
                .map(u -> new AllocationCalculator.Share(u.getId(), u.getIdealFraction())).toList();
        Map<UUID, BigDecimal> result = AllocationCalculator.allocate(request.method(), request.amount(), shares,
                request.consumption());
        BigDecimal total = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Plan(condominium, account, competence, dueDate, active, result, responsibles(active), total);
    }

    /** Responsável financeiro vigente; na falta, o primeiro proprietário. */
    private Map<UUID, Person> responsibles(List<Unit> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Occupancy>> byUnit = occupancies.findCurrentByUnits(list.stream().map(Unit::getId).toList()).stream()
                .collect(Collectors.groupingBy(o -> o.getUnit().getId()));
        Map<UUID, Person> result = new java.util.HashMap<>();
        byUnit.forEach((unitId, occ) -> occ.stream().filter(Occupancy::isFinancialResponsible).findFirst()
                .or(() -> occ.stream().filter(o -> o.getRole() == Occupancy.Role.OWNER).findFirst())
                .ifPresent(o -> result.put(unitId, o.getPerson())));
        return result;
    }

    private AllocationPreview toPreview(Plan plan) {
        List<AllocationLine> lines = new ArrayList<>();
        int withoutResponsible = 0;
        for (Unit u : plan.units()) {
            BigDecimal value = plan.shares().get(u.getId());
            if (value == null) {
                continue;
            }
            Person p = plan.responsible().get(u.getId());
            if (p == null) {
                withoutResponsible++;
            }
            lines.add(new AllocationLine(u.getId(), u.label(), u.getIdealFraction(), p == null ? null : p.getName(), value));
        }
        return new AllocationPreview(plan.competence(), plan.dueDate(), plan.total(), lines.size(), withoutResponsible, lines);
    }

    private AllocationResponse toResponse(Allocation a, ChartAccount account) {
        List<FinancialEntry> charges = entries.findByAllocationId(a.getId());
        long paid = charges.stream().filter(e -> e.getStatus() == FinancialEntry.Status.PAID).count();
        BigDecimal received = charges.stream().map(FinancialEntry::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AllocationResponse(a.getId(), a.getCompetence(), a.getDescription(),
                account == null ? null : account.getCode(), account == null ? null : account.getName(), a.getMethod(),
                a.getBaseAmount(), a.getDueDate(), a.getUnitsCount(), a.getGeneratedAmount(), a.getStatus(), paid, received,
                a.getCancelReason(), a.getCreatedAt());
    }
}
