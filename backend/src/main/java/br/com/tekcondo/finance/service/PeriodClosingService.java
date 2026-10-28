package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.entity.PeriodClosing;
import br.com.tekcondo.finance.repository.BankAccountRepository;
import br.com.tekcondo.finance.repository.PeriodClosingRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fechamento mensal: congela o período (até o último dia de um mês já encerrado), grava uma fotografia dos saldos
 * e totais para a prestação de contas e passa a bloquear alterações retroativas (ver {@link PeriodLock}).
 */
@Service
public class PeriodClosingService {

    public record CloseRequest(@NotNull(message = "Informe o último dia do período a fechar.") LocalDate until,
                               @Size(max = 500) String notes) {
    }

    /** {@code until} nulo reabre todo o histórico. */
    public record ReopenRequest(LocalDate until,
                                @NotBlank(message = "Informe a justificativa.") @Size(min = 5, max = 255,
                                        message = "A justificativa deve ter entre 5 e 255 caracteres.") String reason) {
    }

    public record AccountBalance(UUID id, String name, BigDecimal balance, BigDecimal reconciledBalance) {
    }

    /** Conferência antes de fechar: pendências que costumam indicar erro (não impedem o fechamento). */
    public record ClosingCheck(LocalDate from, LocalDate until, long unreconciledMovements, long pendingApprovals,
                               BigDecimal income, BigDecimal expense, List<AccountBalance> balances) {
    }

    public record HistoryItem(UUID id, PeriodClosing.Action action, LocalDate closedUntil, LocalDate previousUntil, String notes,
                              Map<String, Object> snapshot, Instant createdAt, String createdByName) {
    }

    public record Status(LocalDate closedUntil, LocalDate suggestedUntil, List<HistoryItem> history) {
    }

    private final PeriodLock lock;
    private final PeriodClosingRepository closings;
    private final BankAccountRepository bankAccounts;
    private final FinanceQueries queries;
    private final NamedParameterJdbcTemplate jdbc;
    private final AcessoCondominio access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public PeriodClosingService(PeriodLock lock, PeriodClosingRepository closings, BankAccountRepository bankAccounts,
                                FinanceQueries queries, NamedParameterJdbcTemplate jdbc, AcessoCondominio access,
                                AuditoriaService auditService, Clock clock) {
        this.lock = lock;
        this.closings = closings;
        this.bankAccounts = bankAccounts;
        this.queries = queries;
        this.jdbc = jdbc;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Status status(UUID condominiumId) {
        access.exigir(condominiumId, Permissao.FINANCE_VIEW);
        LocalDate closed = lock.closedUntil(condominiumId);
        LocalDate lastEnded = LocalDate.now(clock).withDayOfMonth(1).minusDays(1);
        LocalDate suggested = closed == null ? lastEnded : endOfMonth(closed.plusDays(1));
        return new Status(closed, suggested.isAfter(lastEnded) ? null : suggested,
                closings.findByCondominiumIdOrderByCreatedAtDesc(condominiumId).stream().map(PeriodClosingService::toItem).toList());
    }

    @Transactional(readOnly = true)
    public ClosingCheck check(UUID condominiumId, LocalDate until) {
        access.exigir(condominiumId, Permissao.FINANCE_VIEW);
        return buildCheck(condominiumId, lock.closedUntil(condominiumId), until);
    }

    @Transactional
    public Status close(UUID condominiumId, CloseRequest request) {
        Condominio condominium = access.exigirAtivo(condominiumId, Permissao.FINANCE_CLOSE);
        LocalDate until = request.until();
        if (!until.equals(endOfMonth(until))) {
            throw ApiException.requisicaoInvalida("CLOSING_NOT_MONTH_END", "O fechamento é mensal: informe o último dia do mês.");
        }
        if (!until.isBefore(LocalDate.now(clock).withDayOfMonth(1))) {
            throw ApiException.regraNegocio("CLOSING_MONTH_NOT_ENDED", "Só é possível fechar meses já encerrados.");
        }
        LocalDate previous = lock.lockForClosing(condominiumId);
        if (previous != null && !until.isAfter(previous)) {
            throw ApiException.conflito("PERIOD_ALREADY_CLOSED", "O período até " + previous + " já está fechado.");
        }
        ClosingCheck check = buildCheck(condominiumId, previous, until);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("from", check.from().toString());
        snapshot.put("until", until.toString());
        snapshot.put("income", check.income().toPlainString());
        snapshot.put("expense", check.expense().toPlainString());
        snapshot.put("unreconciledMovements", check.unreconciledMovements());
        snapshot.put("pendingApprovals", check.pendingApprovals());
        snapshot.put("balances", check.balances().stream().map(b -> Map.of("account", b.name(),
                "balance", b.balance().toPlainString(), "reconciled", b.reconciledBalance().toPlainString())).toList());
        var user = access.usuario();
        PeriodClosing closing = closings.save(new PeriodClosing(condominium.getIdAdministradora(), condominiumId,
                PeriodClosing.Action.CLOSE, until, previous, blank(request.notes()), snapshot, user.idUsuario(), user.nome()));
        lock.setClosedUntil(condominiumId, until);
        auditService.registrar(AcaoAuditoria.PERIOD_CLOSED, "Condominium", condominiumId, Map.of("until", until.toString(),
                "previous", String.valueOf(previous), "closingId", closing.getId().toString()));
        return status(condominiumId);
    }

    @Transactional
    public Status reopen(UUID condominiumId, ReopenRequest request) {
        Condominio condominium = access.exigir(condominiumId, Permissao.FINANCE_CLOSE);
        LocalDate current = lock.lockForClosing(condominiumId);
        if (current == null) {
            throw ApiException.regraNegocio("NOTHING_CLOSED", "Não há período fechado para reabrir.");
        }
        LocalDate until = request.until();
        if (until != null && (!until.equals(endOfMonth(until)) || !until.isBefore(current))) {
            throw ApiException.requisicaoInvalida("INVALID_REOPEN_DATE",
                    "Informe o último dia de um mês anterior ao fechamento atual (ou deixe em branco para reabrir tudo).");
        }
        var user = access.usuario();
        closings.save(new PeriodClosing(condominium.getIdAdministradora(), condominiumId, PeriodClosing.Action.REOPEN, until, current,
                request.reason().trim(), null, user.idUsuario(), user.nome()));
        lock.setClosedUntil(condominiumId, until);
        auditService.registrar(AcaoAuditoria.PERIOD_REOPENED, "Condominium", condominiumId, Map.of("from", current.toString(),
                "to", String.valueOf(until), "reason", request.reason().trim()));
        return status(condominiumId);
    }

    // ------------------------------------------------------------------

    private ClosingCheck buildCheck(UUID condominiumId, LocalDate previous, LocalDate until) {
        LocalDate from = previous == null ? firstMovement(condominiumId, until) : previous.plusDays(1);
        MapSqlParameterSource p = new MapSqlParameterSource("c", condominiumId).addValue("from", Date.valueOf(from))
                .addValue("until", Date.valueOf(until));
        Long unreconciled = jdbc.queryForObject("""
                select (select count(*) from entry_payment where condominium_id = :c and reversed_at is null
                          and reconciled_at is null and paid_on <= :until)
                     + (select count(*) from bank_transfer where condominium_id = :c and reversed_at is null
                          and reconciled_at is null and transfer_date <= :until)
                """, p, Long.class);
        Long pending = jdbc.queryForObject("""
                select count(*) from financial_entry where condominium_id = :c and approval = 'PENDING'
                   and status in ('OPEN', 'PARTIAL') and due_date <= :until
                """, p, Long.class);
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select e.kind, coalesce(sum(pay.total), 0) as total
                  from entry_payment pay join financial_entry e on e.id = pay.entry_id
                 where pay.condominium_id = :c and pay.reversed_at is null and pay.paid_on between :from and :until
                 group by e.kind
                """, p, rs -> {
            totals["INCOME".equals(rs.getString("kind")) ? 0 : 1] = rs.getBigDecimal("total");
        });
        Map<UUID, BigDecimal> balances = queries.balances(condominiumId, until);
        List<AccountBalance> list = new ArrayList<>();
        for (BankAccount b : bankAccounts.findByCondominiumIdOrderByNameAsc(condominiumId)) {
            list.add(new AccountBalance(b.getId(), b.getName(), balances.getOrDefault(b.getId(), b.getOpeningBalance()),
                    queries.reconciledBalance(b.getId(), b.getOpeningBalance(), until)));
        }
        return new ClosingCheck(from, until, unreconciled == null ? 0 : unreconciled, pending == null ? 0 : pending,
                totals[0], totals[1], list);
    }

    private LocalDate firstMovement(UUID condominiumId, LocalDate fallback) {
        LocalDate first = jdbc.queryForObject("""
                select least((select min(paid_on) from entry_payment where condominium_id = :c),
                             (select min(transfer_date) from bank_transfer where condominium_id = :c),
                             (select min(opening_date) from bank_account where condominium_id = :c))
                """, new MapSqlParameterSource("c", condominiumId), LocalDate.class);
        return first == null || first.isAfter(fallback) ? fallback.withDayOfMonth(1) : first;
    }

    private static HistoryItem toItem(PeriodClosing c) {
        return new HistoryItem(c.getId(), c.getAction(), c.getClosedUntil(), c.getPreviousUntil(), c.getNotes(), c.getSnapshot(),
                c.getCreatedAt(), c.getCreatedByName());
    }

    private static LocalDate endOfMonth(LocalDate d) {
        return d.withDayOfMonth(d.lengthOfMonth());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
