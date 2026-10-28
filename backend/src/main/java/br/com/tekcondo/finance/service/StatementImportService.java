package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.dto.FinanceDtos.EntryDetail;
import br.com.tekcondo.finance.dto.FinanceDtos.EntryRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.PaymentRequest;
import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.entity.BankTransfer;
import br.com.tekcondo.finance.entity.EntryPayment;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.entity.StatementImport;
import br.com.tekcondo.finance.entity.StatementLine;
import br.com.tekcondo.finance.repository.BankTransferRepository;
import br.com.tekcondo.finance.repository.EntryPaymentRepository;
import br.com.tekcondo.finance.repository.StatementImportRepository;
import br.com.tekcondo.finance.repository.StatementLineRepository;
import br.com.tekcondo.finance.service.StatementMatcher.Candidate;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.banco.OfxParser;
import br.com.tekcondo.comum.exception.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação de extratos OFX e conciliação linha a linha: casamento automático inequívoco, casamento manual,
 * "ignorar" com justificativa e lançamento direto (tarifas, rendimentos) a partir da linha do extrato.
 */
@Service
public class StatementImportService {

    /** Janela de datas aceita no casamento manual (compensação de cheques, crédito D+n etc.). */
    private static final int MANUAL_TOLERANCE_DAYS = 31;

    public record ImportResult(UUID importId, String fileName, LocalDate periodStart, LocalDate periodEnd, int linesInFile,
                               int newLines, int duplicates, int autoMatched, int pending) {
    }

    public record Suggestion(String type, UUID id, LocalDate date, BigDecimal amount, String description) {
    }

    public record LineView(UUID id, String fitId, LocalDate postedOn, BigDecimal amount, String trnType, String memo,
                           StatementLine.Status status, String matchedType, UUID matchedId, String note, List<Suggestion> suggestions) {
    }

    public record ImportSummary(UUID id, String fileName, LocalDate periodStart, LocalDate periodEnd, int linesTotal, int linesNew,
                                int autoMatched, Instant createdAt, String createdByName) {
    }

    public record MatchRequest(@NotBlank(message = "Informe o tipo da movimentação.") String type,
                               @NotNull(message = "Informe a movimentação.") UUID movementId) {
    }

    public record IgnoreRequest(@NotBlank(message = "Informe o motivo.") @Size(min = 3, max = 255) String note) {
    }

    public record RegisterRequest(@NotNull(message = "Informe a conta do plano de contas.") UUID chartAccountId,
                                  @NotBlank(message = "Informe a descrição.") @Size(max = 200) String description,
                                  UUID costCenterId) {
    }

    private final StatementImportRepository imports;
    private final StatementLineRepository lines;
    private final EntryPaymentRepository payments;
    private final BankTransferRepository transfers;
    private final FinanceSetupService setup;
    private final FinanceQueries queries;
    private final EntryService entryService;
    private final PeriodLock periodLock;
    private final CondominiumAccess access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public StatementImportService(StatementImportRepository imports, StatementLineRepository lines, EntryPaymentRepository payments,
                                  BankTransferRepository transfers, FinanceSetupService setup, FinanceQueries queries,
                                  EntryService entryService, PeriodLock periodLock, CondominiumAccess access,
                                  AuditoriaService auditService, Clock clock) {
        this.imports = imports;
        this.lines = lines;
        this.payments = payments;
        this.transfers = transfers;
        this.setup = setup;
        this.queries = queries;
        this.entryService = entryService;
        this.periodLock = periodLock;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importOfx(UUID condominiumId, UUID accountId, String fileName, byte[] content, boolean ignoreAccountCheck) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        BankAccount account = setup.loadBankAccount(condominiumId, accountId);
        if (!account.isActive()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta bancária está inativa.");
        }
        String hash = OfxParser.sha256(content);
        if (imports.existsByBankAccountIdAndFileHash(accountId, hash)) {
            throw ApiException.conflito("STATEMENT_ALREADY_IMPORTED", "Este arquivo de extrato já foi importado nesta conta.");
        }
        OfxParser.Extrato statement = OfxParser.parse(content);
        if (statement.transacoes().isEmpty()) {
            throw ApiException.regraNegocio("STATEMENT_EMPTY", "O extrato não contém transações.");
        }
        if (!ignoreAccountCheck && !sameAccount(account.getAccountNumber(), statement.idConta())) {
            throw ApiException.regraNegocio("STATEMENT_ACCOUNT_MISMATCH", "O extrato é da conta " + statement.idConta()
                    + ", diferente da conta cadastrada (" + account.getAccountNumber() + "). Confira o arquivo.");
        }
        var user = access.user();
        StatementImport imp = imports.save(new StatementImport(condominium.getOrganizationId(), condominiumId, accountId,
                safeName(fileName), hash, statement.inicio(), statement.fim(), statement.transacoes().size(), user.idUsuario(), user.nome()));
        Set<String> existing = lines.existingFitIds(accountId, statement.transacoes().stream().map(OfxParser.Transacao::fitId).toList());
        int created = 0;
        for (OfxParser.Transacao t : statement.transacoes()) {
            if (existing.contains(t.fitId())) {
                continue;
            }
            lines.save(new StatementLine(condominium.getOrganizationId(), condominiumId, accountId, imp.getId(), t.fitId(),
                    t.dataLancamento(), t.valor(), t.tipo(), t.historico()));
            created++;
        }
        lines.flush();
        int matched = autoMatch(condominiumId, account);
        imp.result(created, matched);
        int pending = (int) lines.findByAccount(accountId, StatementLine.Status.UNMATCHED).size();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("account", account.getName());
        details.put("file", imp.getFileName());
        details.put("newLines", created);
        details.put("autoMatched", matched);
        auditService.registrar(AcaoAuditoria.STATEMENT_IMPORTED, "BankAccount", accountId, details);
        return new ImportResult(imp.getId(), imp.getFileName(), statement.inicio(), statement.fim(), statement.transacoes().size(),
                created, statement.transacoes().size() - created, matched, pending);
    }

    @Transactional(readOnly = true)
    public List<ImportSummary> imports(UUID condominiumId, UUID accountId) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        setup.loadBankAccount(condominiumId, accountId);
        return imports.findTop20ByBankAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(i -> new ImportSummary(i.getId(), i.getFileName(), i.getPeriodStart(), i.getPeriodEnd(), i.getLinesTotal(),
                        i.getLinesNew(), i.getAutoMatched(), i.getCreatedAt(), i.getCreatedByName())).toList();
    }

    @Transactional(readOnly = true)
    public List<LineView> lines(UUID condominiumId, UUID accountId, StatementLine.Status status) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        setup.loadBankAccount(condominiumId, accountId);
        List<StatementLine> list = lines.findByAccount(accountId, status);
        List<Candidate> candidates = list.stream().anyMatch(l -> l.getStatus() == StatementLine.Status.UNMATCHED)
                ? openCandidates(accountId, list.stream().map(StatementLine::getPostedOn).min(Comparator.naturalOrder()).orElseThrow(),
                list.stream().map(StatementLine::getPostedOn).max(Comparator.naturalOrder()).orElseThrow(), MANUAL_TOLERANCE_DAYS)
                : List.of();
        return list.stream().map(l -> toView(l, l.getStatus() == StatementLine.Status.UNMATCHED
                ? StatementMatcher.suggestions(toLine(l), candidates, MANUAL_TOLERANCE_DAYS).stream().limit(5)
                .map(c -> new Suggestion(c.type(), c.id(), c.date(), c.amount(), c.description())).toList()
                : List.of())).toList();
    }

    @Transactional
    public LineView match(UUID condominiumId, UUID lineId, MatchRequest request) {
        access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        StatementLine line = load(condominiumId, lineId);
        Candidate candidate = openCandidates(line.getBankAccountId(), line.getPostedOn(), line.getPostedOn(), MANUAL_TOLERANCE_DAYS)
                .stream().filter(c -> c.type().equals(request.type()) && c.id().equals(request.movementId())).findFirst()
                .orElseThrow(() -> ApiException.regraNegocio("MOVEMENT_NOT_ELIGIBLE",
                        "A movimentação não pertence a esta conta, já está conciliada ou está fora da janela de datas."));
        if (!StatementMatcher.compatible(toLine(line), candidate, MANUAL_TOLERANCE_DAYS)) {
            throw ApiException.regraNegocio("AMOUNT_MISMATCH", "O valor da movimentação é diferente do valor do extrato.");
        }
        apply(condominiumId, line, candidate);
        auditService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "StatementLine", lineId, Map.of("matched", candidate.key()));
        return toView(line, List.of());
    }

    @Transactional
    public LineView unmatch(UUID condominiumId, UUID lineId) {
        access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        StatementLine line = load(condominiumId, lineId);
        periodLock.ensureOpen(condominiumId, line.getPostedOn());
        UUID by = access.user().idUsuario();
        Instant now = Instant.now(clock);
        if (line.getPaymentId() != null) {
            payments.findAllInCondominium(List.of(line.getPaymentId()), condominiumId).forEach(p -> {
                periodLock.ensureOpen(condominiumId, p.getPaidOn());
                p.reconcile(false, by, now);
            });
        }
        if (line.getTransferId() != null) {
            transfers.findAllInCondominium(List.of(line.getTransferId()), condominiumId).forEach(t -> {
                periodLock.ensureOpen(condominiumId, t.getTransferDate());
                t.reconcile(false, by, now);
            });
        }
        line.reset();
        auditService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "StatementLine", lineId, Map.of("matched", false));
        return toView(line, List.of());
    }

    @Transactional
    public LineView ignore(UUID condominiumId, UUID lineId, IgnoreRequest request) {
        access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        StatementLine line = load(condominiumId, lineId);
        periodLock.ensureOpen(condominiumId, line.getPostedOn());
        line.ignore(request.note().trim(), access.user().idUsuario(), Instant.now(clock));
        auditService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "StatementLine", lineId, Map.of("ignored", request.note().trim()));
        return toView(line, List.of());
    }

    /**
     * Lança a receita/despesa que só existe no extrato (tarifa, rendimento, IOF) já baixada na data do extrato e a
     * concilia. Despesas em condomínio que exige aprovação passam pela aprovação — o usuário precisa dessa permissão.
     */
    @Transactional
    public LineView register(UUID condominiumId, UUID lineId, RegisterRequest request) {
        StatementLine line = load(condominiumId, lineId);
        if (line.getStatus() != StatementLine.Status.UNMATCHED) {
            throw ApiException.regraNegocio("STATEMENT_LINE_RESOLVED", "Esta linha do extrato já foi conciliada ou ignorada.");
        }
        FinancialEntry.Kind kind = line.getAmount().signum() > 0 ? FinancialEntry.Kind.INCOME : FinancialEntry.Kind.EXPENSE;
        BigDecimal value = line.getAmount().abs();
        EntryDetail entry = entryService.create(condominiumId, new EntryRequest(kind, request.description(), request.chartAccountId(),
                request.costCenterId(), null, null, null, value, line.getPostedOn(), line.getPostedOn(), null,
                "Lançado a partir do extrato bancário (" + line.getFitId() + ")", null, null)).getFirst();
        if (entry.summary().approval() == FinancialEntry.Approval.PENDING) {
            entry = entryService.approve(condominiumId, entry.summary().id(), true, "Débito já efetivado no extrato bancário");
        }
        entry = entryService.pay(condominiumId, entry.summary().id(), new PaymentRequest(line.getBankAccountId(), line.getPostedOn(),
                value, null, null, null, EntryPayment.Method.DEBIT, "Extrato " + line.getFitId()));
        UUID paymentId = entry.payments().getLast().id();
        EntryPayment payment = payments.findAllInCondominium(List.of(paymentId), condominiumId).getFirst();
        apply(condominiumId, line, new Candidate("PAYMENT", paymentId, payment.getPaidOn(), line.getAmount(), request.description()));
        return toView(line, List.of());
    }

    // ------------------------------------------------------------------

    private int autoMatch(UUID condominiumId, BankAccount account) {
        LocalDate closed = periodLock.closedUntil(condominiumId);
        List<StatementLine> pending = lines.findByAccount(account.getId(), StatementLine.Status.UNMATCHED).stream()
                .filter(l -> closed == null || l.getPostedOn().isAfter(closed)).toList();
        if (pending.isEmpty()) {
            return 0;
        }
        LocalDate from = pending.stream().map(StatementLine::getPostedOn).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = pending.stream().map(StatementLine::getPostedOn).max(Comparator.naturalOrder()).orElseThrow();
        List<Candidate> candidates = openCandidates(account.getId(), from, to, StatementMatcher.DEFAULT_TOLERANCE_DAYS).stream()
                .filter(c -> closed == null || c.date().isAfter(closed)).toList();
        Map<UUID, Candidate> pairs = StatementMatcher.autoMatch(pending.stream().map(StatementImportService::toLine).toList(),
                candidates, StatementMatcher.DEFAULT_TOLERANCE_DAYS);
        pending.stream().filter(l -> pairs.containsKey(l.getId())).forEach(l -> apply(condominiumId, l, pairs.get(l.getId())));
        return pairs.size();
    }

    private void apply(UUID condominiumId, StatementLine line, Candidate c) {
        periodLock.ensureOpen(condominiumId, line.getPostedOn(), c.date());
        UUID by = access.user().idUsuario();
        Instant now = Instant.now(clock);
        if ("PAYMENT".equals(c.type())) {
            payments.findAllInCondominium(List.of(c.id()), condominiumId).forEach(p -> p.reconcile(true, by, now));
            line.matchPayment(c.id(), by, now);
        } else {
            transfers.findAllInCondominium(List.of(c.id()), condominiumId).forEach(t -> t.reconcile(true, by, now));
            line.matchTransfer(c.id(), by, now);
        }
    }

    /** Movimentações ainda não conciliadas da conta no intervalo (ampliado pela tolerância). */
    private List<Candidate> openCandidates(UUID accountId, LocalDate from, LocalDate to, int toleranceDays) {
        List<Candidate> result = new ArrayList<>();
        queries.movements(accountId, BigDecimal.ZERO, from.minusDays(toleranceDays), to.plusDays(toleranceDays)).stream()
                .filter(m -> !m.reconciled())
                .forEach(m -> result.add(new Candidate(m.type(), m.id(), m.date(), m.amount(), m.description())));
        return result;
    }

    private StatementLine load(UUID condominiumId, UUID lineId) {
        return lines.findInCondominium(lineId, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Linha do extrato"));
    }

    private static StatementMatcher.Line toLine(StatementLine l) {
        return new StatementMatcher.Line(l.getId(), l.getPostedOn(), l.getAmount());
    }

    private static LineView toView(StatementLine l, List<Suggestion> suggestions) {
        String type = l.getPaymentId() != null ? "PAYMENT" : l.getTransferId() != null ? "TRANSFER" : null;
        return new LineView(l.getId(), l.getFitId(), l.getPostedOn(), l.getAmount(), l.getTrnType(), l.getMemo(), l.getStatus(), type,
                l.getPaymentId() != null ? l.getPaymentId() : l.getTransferId(), l.getNote(), suggestions);
    }

    /** Compara só os dígitos; aceita quando um contém o outro (com/sem dígito verificador, zeros à esquerda). */
    static boolean sameAccount(String registered, String fromFile) {
        if (registered == null || fromFile == null) {
            return true;
        }
        String a = registered.replaceAll("\\D", "").replaceFirst("^0+", "");
        String b = fromFile.replaceAll("\\D", "").replaceFirst("^0+", "");
        return a.isEmpty() || b.isEmpty() || a.contains(b) || b.contains(a);
    }

    private static String safeName(String name) {
        String n = name == null || name.isBlank() ? "extrato.ofx" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
