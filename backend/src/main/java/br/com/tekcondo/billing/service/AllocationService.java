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

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationLine;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationPreview;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationRequest;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationResponse;
import br.com.tekcondo.billing.entity.Allocation;
import br.com.tekcondo.billing.repository.AllocationRepository;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.ChartAccountRepository;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.finance.service.FinanceSetupService;
import br.com.tekcondo.finance.service.PeriodLock;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rateio e geração das cobranças mensais (um título a receber por unidade). */
@Service
public class AllocationService {

    private final AllocationRepository allocations;
    private final FinancialEntryRepository entries;
    private final ChartAccountRepository chartAccounts;
    private final UnidadeRepository units;
    private final VinculoRepository occupancies;
    private final FinanceSetupService setup;
    private final PeriodLock periodLock;
    private final AcessoCondominio access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public AllocationService(AllocationRepository allocations, FinancialEntryRepository entries,
                             ChartAccountRepository chartAccounts, UnidadeRepository units, VinculoRepository occupancies,
                             FinanceSetupService setup, PeriodLock periodLock, AcessoCondominio access, AuditoriaService auditService,
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

    private record Plan(Condominio condominium, ChartAccount account, LocalDate competence, LocalDate dueDate,
                        List<Unidade> units, Map<UUID, BigDecimal> shares, Map<UUID, Pessoa> responsible, BigDecimal total) {
    }

    @Transactional(readOnly = true)
    public AllocationPreview preview(UUID condominiumId, AllocationRequest request) {
        return toPreview(plan(condominiumId, request));
    }

    @Transactional
    public AllocationResponse generate(UUID condominiumId, AllocationRequest request) {
        Plan plan = plan(condominiumId, request);
        periodLock.ensureOpen(condominiumId, plan.competence());
        UUID userId = access.usuario().idUsuario();
        Allocation allocation = allocations.save(new Allocation(plan.condominium().getIdAdministradora(), condominiumId,
                plan.competence(), request.description().trim(), plan.account().getId(), request.method(),
                request.method() == AllocationCalculator.Method.CONSUMPTION ? plan.total() : request.amount(),
                plan.dueDate(), plan.shares().size(), plan.total(), userId));
        for (Unidade unit : plan.units()) {
            BigDecimal value = plan.shares().get(unit.getId());
            if (value == null || value.signum() <= 0) {
                continue;
            }
            FinancialEntry charge = new FinancialEntry(plan.condominium().getIdAdministradora(), condominiumId,
                    FinancialEntry.Kind.INCOME, FinancialEntry.Origin.ALLOCATION, userId);
            charge.details(request.description().trim(), plan.account(), null, null, unit, plan.responsible().get(unit.getId()),
                    value, plan.dueDate(), plan.competence(), null, null, userId);
            charge.lateFees(plan.condominium().getPercentualMulta(), plan.condominium().getPercentualJurosMensal());
            charge.linkAllocation(allocation.getId());
            entries.save(charge);
        }
        auditService.registrar(AcaoAuditoria.ALLOCATION_GENERATED, "Allocation", allocation.getId(), Map.of(
                "competence", plan.competence().toString(), "method", request.method().name(),
                "units", plan.shares().size(), "total", plan.total().toPlainString()));
        return toResponse(allocation, plan.account());
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> list(UUID condominiumId) {
        access.exigir(condominiumId, Permissao.BILLING_VIEW);
        Map<UUID, ChartAccount> accounts = chartAccounts.findAvailable(access.idAdministradora()).stream()
                .collect(Collectors.toMap(ChartAccount::getId, a -> a));
        return allocations.findByCondominiumIdOrderByCompetenceDescCreatedAtDesc(condominiumId).stream()
                .map(a -> toResponse(a, accounts.get(a.getChartAccountId()))).toList();
    }

    /** Cancela o rateio e suas cobranças — somente se nenhuma tiver sido paga. */
    @Transactional
    public AllocationResponse cancel(UUID condominiumId, UUID id, String reason) {
        access.exigir(condominiumId, Permissao.BILLING_EDIT);
        access.exigir(condominiumId, Permissao.FINANCE_CANCEL);
        Allocation allocation = allocations.findByIdAndCondominiumId(id, condominiumId)
                .orElseThrow(() -> ApiException.naoEncontrado("Rateio"));
        periodLock.ensureOpen(condominiumId, allocation.getCompetence());
        List<FinancialEntry> charges = entries.findByAllocationId(id);
        if (charges.stream().anyMatch(e -> e.getPaidAmount().signum() > 0)) {
            throw ApiException.regraNegocio("ALLOCATION_HAS_PAYMENTS",
                    "Há cobranças deste rateio com pagamento. Estorne as baixas ou cancele as cobranças individualmente.");
        }
        UUID by = access.usuario().idUsuario();
        Instant now = Instant.now(clock);
        charges.stream().filter(e -> e.getStatus() == FinancialEntry.Status.OPEN)
                .forEach(e -> e.cancel("Rateio cancelado: " + reason.trim(), by, now));
        allocation.cancel(reason.trim(), by, now);
        auditService.registrar(AcaoAuditoria.ALLOCATION_CANCELLED, "Allocation", id, Map.of("reason", reason.trim()));
        return toResponse(allocation, chartAccounts.findById(allocation.getChartAccountId()).orElse(null));
    }

    // ------------------------------------------------------------------

    private Plan plan(UUID condominiumId, AllocationRequest request) {
        Condominio condominium = access.exigirAtivo(condominiumId, Permissao.BILLING_EDIT);
        ChartAccount account = setup.analyticAccount(request.chartAccountId(), ChartAccount.Nature.INCOME);
        LocalDate competence = YearMonth.parse(request.competence()).atDay(1);
        if (allocations.existsGenerated(condominiumId, competence, account.getId())) {
            throw ApiException.conflito("ALLOCATION_EXISTS", "Já existe cobrança gerada para " + account.getName()
                    + " na competência " + YearMonth.from(competence) + ". Cancele a anterior para gerar novamente.");
        }
        if (request.method() != AllocationCalculator.Method.CONSUMPTION && request.amount() == null) {
            throw ApiException.requisicaoInvalida("AMOUNT_REQUIRED", "Informe o valor a ratear.");
        }
        LocalDate dueDate = request.dueDate() != null ? request.dueDate()
                : competence.withDayOfMonth(condominium.getDiaVencimento());
        List<Unidade> active = units.buscarAtivasDoCondominio(condominiumId).stream()
                .sorted(Comparator.comparing(Unidade::rotulo)).toList();
        List<AllocationCalculator.Share> shares = active.stream()
                .map(u -> new AllocationCalculator.Share(u.getId(), u.getFracaoIdeal())).toList();
        Map<UUID, BigDecimal> result = AllocationCalculator.allocate(request.method(), request.amount(), shares,
                request.consumption());
        BigDecimal total = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Plan(condominium, account, competence, dueDate, active, result, responsibles(active), total);
    }

    /** Responsável financeiro vigente; na falta, o primeiro proprietário. */
    private Map<UUID, Pessoa> responsibles(List<Unidade> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Vinculo>> byUnit = occupancies.buscarVigentesPorUnidades(list.stream().map(Unidade::getId).toList()).stream()
                .collect(Collectors.groupingBy(o -> o.getUnidade().getId()));
        Map<UUID, Pessoa> result = new java.util.HashMap<>();
        byUnit.forEach((unitId, occ) -> occ.stream().filter(Vinculo::isResponsavelFinanceiro).findFirst()
                .or(() -> occ.stream().filter(o -> o.getTipo() == Vinculo.Tipo.OWNER).findFirst())
                .ifPresent(o -> result.put(unitId, o.getPessoa())));
        return result;
    }

    private AllocationPreview toPreview(Plan plan) {
        List<AllocationLine> lines = new ArrayList<>();
        int withoutResponsible = 0;
        for (Unidade u : plan.units()) {
            BigDecimal value = plan.shares().get(u.getId());
            if (value == null) {
                continue;
            }
            Pessoa p = plan.responsible().get(u.getId());
            if (p == null) {
                withoutResponsible++;
            }
            lines.add(new AllocationLine(u.getId(), u.rotulo(), u.getFracaoIdeal(), p == null ? null : p.getNome(), value));
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
