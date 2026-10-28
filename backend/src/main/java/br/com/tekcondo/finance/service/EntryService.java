package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.dto.FinanceDtos.EntryDetail;
import br.com.tekcondo.finance.dto.FinanceDtos.EntryRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.EntrySummary;
import br.com.tekcondo.finance.dto.FinanceDtos.PaymentQuote;
import br.com.tekcondo.finance.dto.FinanceDtos.PaymentRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.PaymentResponse;
import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.CostCenter;
import br.com.tekcondo.finance.entity.EntryPayment;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.EntryPaymentRepository;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.people.repository.PersonRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.UnitRepository;
import br.com.tekcondo.supplier.entity.Supplier;
import br.com.tekcondo.supplier.repository.SupplierRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas a pagar e a receber: lançamento, aprovação, baixa, estorno e cancelamento. */
@Service
public class EntryService {

    private static final String ENTITY = "FinancialEntry";

    private final FinancialEntryRepository entries;
    private final EntryPaymentRepository payments;
    private final SupplierRepository suppliers;
    private final UnitRepository units;
    private final PersonRepository people;
    private final FinanceSetupService setup;
    private final PeriodLock periodLock;
    private final CondominiumAccess access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public EntryService(FinancialEntryRepository entries, EntryPaymentRepository payments, SupplierRepository suppliers,
                        UnitRepository units, PersonRepository people, FinanceSetupService setup,
                        PeriodLock periodLock, CondominiumAccess access, AuditoriaService auditService, Clock clock) {
        this.entries = entries;
        this.payments = payments;
        this.suppliers = suppliers;
        this.units = units;
        this.people = people;
        this.setup = setup;
        this.periodLock = periodLock;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    public record Filter(FinancialEntry.Kind kind, List<FinancialEntry.Status> statuses, LocalDate dueFrom, LocalDate dueTo,
                         UUID chartAccountId, UUID supplierId, UUID unitId, String search, Boolean overdue,
                         FinancialEntry.Approval approval) {
    }

    @Transactional(readOnly = true)
    public PaginaResponse<EntrySummary> list(UUID condominiumId, Filter f, Pageable pageable) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        LocalDate today = LocalDate.now(clock);
        Specification<FinancialEntry> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("condominiumId"), condominiumId));
            if (f.kind() != null) p.add(cb.equal(root.get("kind"), f.kind()));
            if (f.statuses() != null && !f.statuses().isEmpty()) p.add(root.get("status").in(f.statuses()));
            if (f.dueFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("dueDate"), f.dueFrom()));
            if (f.dueTo() != null) p.add(cb.lessThanOrEqualTo(root.get("dueDate"), f.dueTo()));
            if (f.chartAccountId() != null) p.add(cb.equal(root.get("chartAccount").get("id"), f.chartAccountId()));
            if (f.supplierId() != null) p.add(cb.equal(root.get("supplier").get("id"), f.supplierId()));
            if (f.unitId() != null) p.add(cb.equal(root.get("unit").get("id"), f.unitId()));
            if (f.approval() != null) p.add(cb.equal(root.get("approval"), f.approval()));
            if (Boolean.TRUE.equals(f.overdue())) {
                p.add(root.get("status").in(FinancialEntry.Status.OPEN, FinancialEntry.Status.PARTIAL));
                p.add(cb.lessThan(root.get("dueDate"), today));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("documentNumber"), "")), like)));
            }
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("chartAccount");
                root.fetch("supplier", JoinType.LEFT);
                root.fetch("unit", JoinType.LEFT).fetch("building", JoinType.LEFT);
                root.fetch("person", JoinType.LEFT);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PaginaResponse.of(entries.findAll(spec, pageable), e -> summary(e, today));
    }

    @Transactional(readOnly = true)
    public EntryDetail get(UUID condominiumId, UUID id) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        return detail(load(condominiumId, id));
    }

    /** Cria o lançamento (ou a série, quando {@code repeat} > 1: parcelas mensais da mesma despesa/receita). */
    @Transactional
    public List<EntryDetail> create(UUID condominiumId, EntryRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        UUID userId = access.user().idUsuario();
        int repeat = request.repeat() == null ? 1 : request.repeat();
        UUID group = repeat > 1 ? UUID.randomUUID() : null;
        Refs refs = resolve(condominiumId, request);
        List<FinancialEntry> created = new ArrayList<>();
        for (int i = 0; i < repeat; i++) {
            FinancialEntry entry = new FinancialEntry(condominium.getOrganizationId(), condominiumId, request.kind(),
                    repeat > 1 ? FinancialEntry.Origin.RECURRENCE : FinancialEntry.Origin.MANUAL, userId);
            LocalDate due = request.dueDate().plusMonths(i);
            LocalDate competence = (request.competence() == null ? request.dueDate() : request.competence()).plusMonths(i);
            String description = repeat > 1 ? request.description().trim() + " (" + (i + 1) + "/" + repeat + ")" : request.description();
            String document = repeat > 1 && request.documentNumber() != null ? request.documentNumber().trim() + "-" + (i + 1)
                    : blank(request.documentNumber());
            entry.details(description, refs.account, refs.costCenter, refs.supplier, refs.unit, refs.person,
                    request.amount(), due, competence, document, blank(request.notes()), userId);
            if (group != null) {
                entry.recurrence(group, i + 1, repeat);
            }
            entry.requireApproval(request.kind() == FinancialEntry.Kind.EXPENSE && condominium.isRequirePayableApproval());
            if (request.kind() == FinancialEntry.Kind.INCOME && refs.unit != null) {
                entry.lateFees(condominium.getFinePercent(), condominium.getInterestMonthlyPercent());
            }
            ensureNoDuplicateDocument(condominiumId, entry);
            periodLock.ensureOpen(condominiumId, entry.getCompetence());
            entries.save(entry);
            auditService.registrar(AcaoAuditoria.ENTRY_CREATED, ENTITY, entry.getId(), auditDetails(entry));
            created.add(entry);
        }
        entries.flush(); // o número de cobrança é gerado pelo banco no INSERT
        return created.stream().map(this::detail).toList();
    }

    @Transactional
    public EntryDetail update(UUID condominiumId, UUID id, EntryRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        FinancialEntry entry = load(condominiumId, id);
        if (request.version() == null || !request.version().equals(entry.getVersion())) {
            throw ApiException.versaoObsoleta();
        }
        if (request.kind() != entry.getKind()) {
            throw ApiException.regraNegocio("ENTRY_KIND_IMMUTABLE", "Não é possível trocar receita por despesa. Cancele e lance novamente.");
        }
        periodLock.ensureOpen(condominiumId, entry.getCompetence(), request.competence() == null ? request.dueDate() : request.competence());
        Map<String, Object> before = auditDetails(entry);
        BigDecimal previousAmount = entry.getAmount();
        Refs refs = resolve(condominiumId, request);
        entry.details(request.description(), refs.account, refs.costCenter, refs.supplier, refs.unit, refs.person,
                request.amount(), request.dueDate(), request.competence() == null ? request.dueDate() : request.competence(),
                blank(request.documentNumber()), blank(request.notes()), access.user().idUsuario());
        // Despesa já aprovada cujo valor mudou volta para aprovação.
        if (entry.getKind() == FinancialEntry.Kind.EXPENSE && condominium.isRequirePayableApproval()
                && entry.getApproval() == FinancialEntry.Approval.APPROVED && previousAmount.compareTo(request.amount()) != 0) {
            entry.requireApproval(true);
        }
        ensureNoDuplicateDocument(condominiumId, entry);
        entries.flush();
        auditService.registrar(AcaoAuditoria.ENTRY_UPDATED, ENTITY, id, Map.of("from", before, "to", auditDetails(entry)));
        return detail(entry);
    }

    @Transactional
    public EntryDetail approve(UUID condominiumId, UUID id, boolean approved, String note) {
        access.requireActive(condominiumId, Permissao.FINANCE_APPROVE);
        FinancialEntry entry = load(condominiumId, id);
        if (approved) {
            entry.approve(access.user().idUsuario(), Instant.now(clock), blank(note));
        } else {
            if (note == null || note.isBlank()) {
                throw ApiException.requisicaoInvalida("REASON_REQUIRED", "Informe o motivo da reprovação.");
            }
            entry.reject(access.user().idUsuario(), Instant.now(clock), note.trim());
        }
        entries.flush();
        auditService.registrar(approved ? AcaoAuditoria.ENTRY_APPROVED : AcaoAuditoria.ENTRY_REJECTED, ENTITY, id,
                note == null ? null : Map.of("note", note));
        return detail(entry);
    }

    @Transactional
    public EntryDetail cancel(UUID condominiumId, UUID id, String reason) {
        access.require(condominiumId, Permissao.FINANCE_CANCEL);
        FinancialEntry entry = load(condominiumId, id);
        periodLock.ensureOpen(condominiumId, entry.getCompetence());
        entry.cancel(reason.trim(), access.user().idUsuario(), Instant.now(clock));
        entries.flush();
        auditService.registrar(AcaoAuditoria.ENTRY_CANCELLED, ENTITY, id, Map.of("reason", reason.trim(),
                "amount", entry.getAmount().toPlainString()));
        return detail(entry);
    }

    @Transactional(readOnly = true)
    public PaymentQuote quote(UUID condominiumId, UUID id, LocalDate date) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        FinancialEntry entry = load(condominiumId, id);
        LateFeeCalculator.Quote q = LateFeeCalculator.quote(entry.remaining(), entry.getDueDate(),
                date == null ? LocalDate.now(clock) : date, entry.getFinePercent(), entry.getInterestMonthlyPercent());
        return new PaymentQuote(q.principal(), q.daysLate(), q.fine(), q.interest(), q.total());
    }

    /** Baixa total ou parcial. Concorrência protegida pela versão do título (duas baixas simultâneas → 409). */
    @Transactional
    public EntryDetail pay(UUID condominiumId, UUID id, PaymentRequest request) {
        access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        FinancialEntry entry = load(condominiumId, id);
        if (request.paidOn().isAfter(LocalDate.now(clock))) {
            throw ApiException.regraNegocio("FUTURE_PAYMENT", "A data do pagamento não pode ser futura.");
        }
        periodLock.ensureOpen(condominiumId, request.paidOn());
        BankAccount bankAccount = setup.loadBankAccount(condominiumId, request.bankAccountId());
        if (!bankAccount.isActive()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta bancária está inativa.");
        }
        if (request.paidOn().isBefore(bankAccount.getOpeningDate())) {
            throw ApiException.regraNegocio("PAYMENT_BEFORE_OPENING", "A data é anterior à abertura da conta bancária.");
        }
        entry.registerPayment(request.principal(), access.user().idUsuario());
        EntryPayment payment = payments.save(new EntryPayment(entry, bankAccount, request.paidOn(), request.principal(),
                zero(request.fine()), zero(request.interest()), zero(request.discount()), request.method(),
                blank(request.notes()), access.user().idUsuario()));
        entries.flush();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentId", payment.getId().toString());
        details.put("principal", payment.getPrincipal().toPlainString());
        details.put("total", payment.getTotal().toPlainString());
        details.put("paidOn", payment.getPaidOn().toString());
        details.put("bankAccount", bankAccount.getName());
        auditService.registrar(AcaoAuditoria.PAYMENT_REGISTERED, ENTITY, id, details);
        return detail(entry);
    }

    @Transactional
    public EntryDetail reversePayment(UUID condominiumId, UUID paymentId, String reason) {
        access.require(condominiumId, Permissao.FINANCE_CANCEL);
        EntryPayment payment = payments.findInCondominium(paymentId, condominiumId)
                .orElseThrow(() -> ApiException.naoEncontrado("Baixa"));
        periodLock.ensureOpen(condominiumId, payment.getPaidOn());
        payment.reverse(reason.trim(), access.user().idUsuario(), Instant.now(clock));
        FinancialEntry entry = payment.getEntry();
        entry.reversePayment(payment.getPrincipal(), access.user().idUsuario());
        entries.flush();
        auditService.registrar(AcaoAuditoria.PAYMENT_REVERSED, ENTITY, entry.getId(), Map.of("paymentId", paymentId.toString(),
                "total", payment.getTotal().toPlainString(), "reason", reason.trim()));
        return detail(entry);
    }

    // ---------------------------------------------------------------- apoio

    public FinancialEntry load(UUID condominiumId, UUID id) {
        return entries.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Lançamento"));
    }

    private record Refs(ChartAccount account, CostCenter costCenter, Supplier supplier, Unit unit, Person person) {
    }

    private Refs resolve(UUID condominiumId, EntryRequest r) {
        ChartAccount account = setup.analyticAccount(r.chartAccountId(), r.kind() == FinancialEntry.Kind.INCOME
                ? ChartAccount.Nature.INCOME : ChartAccount.Nature.EXPENSE);
        CostCenter costCenter = r.costCenterId() == null ? null : setup.loadCostCenter(condominiumId, r.costCenterId());
        Supplier supplier = r.supplierId() == null ? null : suppliers.findByIdAndOrganizationId(r.supplierId(), access.organizationId())
                .orElseThrow(() -> ApiException.naoEncontrado("Fornecedor"));
        Unit unit = r.unitId() == null ? null : units.findByIdAndCondominiumId(r.unitId(), condominiumId)
                .orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        Person person = r.personId() == null ? null : people.findByIdAndOrganizationId(r.personId(), access.organizationId())
                .orElseThrow(() -> ApiException.naoEncontrado("Pessoa"));
        if (r.kind() == FinancialEntry.Kind.INCOME && supplier != null) {
            throw ApiException.regraNegocio("SUPPLIER_ON_INCOME", "Receitas não são vinculadas a fornecedor.");
        }
        return new Refs(account, costCenter, supplier, unit, person);
    }

    private void ensureNoDuplicateDocument(UUID condominiumId, FinancialEntry entry) {
        if (entry.getSupplier() != null && entry.getDocumentNumber() != null
                && entries.existsSupplierDocument(condominiumId, entry.getSupplier().getId(), entry.getDocumentNumber(), entry.getId())) {
            throw ApiException.conflito("DUPLICATE_DOCUMENT",
                    "Já existe um lançamento deste fornecedor com o documento nº " + entry.getDocumentNumber() + ".");
        }
    }

    public EntrySummary summary(FinancialEntry e, LocalDate today) {
        String installment = e.getInstallment() == null ? null : e.getInstallment() + "/" + e.getInstallments();
        return new EntrySummary(e.getId(), e.getKind(), e.getDescription(), e.getChartAccount().getCode(),
                e.getChartAccount().getName(), e.getSupplier() == null ? null : e.getSupplier().getName(),
                e.getUnit() == null ? null : e.getUnit().label(), e.getPerson() == null ? null : e.getPerson().getName(),
                e.getAmount(), e.getPaidAmount(), e.remaining(), e.getDueDate(), e.getCompetence(), e.getStatus(),
                e.getApproval(), e.isOverdue(today), installment, e.getDocumentNumber(), e.getCollectionNumber());
    }

    private EntryDetail detail(FinancialEntry e) {
        List<PaymentResponse> list = payments.findByEntry(e.getId()).stream().map(PaymentResponse::from).toList();
        return new EntryDetail(summary(e, LocalDate.now(clock)), e.getChartAccount().getId(),
                e.getCostCenter() == null ? null : e.getCostCenter().getId(),
                e.getCostCenter() == null ? null : e.getCostCenter().getName(),
                e.getSupplier() == null ? null : e.getSupplier().getId(), e.getUnit() == null ? null : e.getUnit().getId(),
                e.getPerson() == null ? null : e.getPerson().getId(), e.getNotes(), e.getOrigin(), e.getApprovalNote(),
                e.getApprovedAt(), e.getCancelReason(), e.getCancelledAt(), e.getFinePercent(), e.getInterestMonthlyPercent(),
                e.getVersion(), list);
    }

    private static Map<String, Object> auditDetails(FinancialEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", e.getKind().name());
        m.put("description", e.getDescription());
        m.put("amount", e.getAmount().toPlainString());
        m.put("dueDate", e.getDueDate().toString());
        m.put("account", e.getChartAccount().getCode());
        if (e.getDocumentNumber() != null) m.put("document", e.getDocumentNumber());
        return m;
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
