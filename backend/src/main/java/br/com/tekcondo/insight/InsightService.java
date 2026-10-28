package br.com.tekcondo.insight;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.service.FinanceQueries;
import br.com.tekcondo.identity.access.Permission;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores para decisão, calculados SOMENTE a partir dos dados registrados (sem estimativas inventadas):
 * projeção de caixa pelos vencimentos em aberto e despesas fora do padrão histórico de cada conta.
 */
@Service
public class InsightService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int HISTORY_MONTHS = 6;
    private static final BigDecimal MIN_ABSOLUTE_DEVIATION = new BigDecimal("100.00");

    public record CashProjection(LocalDate asOf, BigDecimal currentBalance, BigDecimal overdueReceivables, BigDecimal overduePayables,
                                 BigDecimal lowestProjectedBalance, LocalDate lowestOn, List<InsightCalculator.Week> weeks,
                                 List<String> notes) {
    }

    public record ExpenseAnomaly(String month, UUID chartAccountId, String accountCode, String accountName, BigDecimal value,
                                 BigDecimal historicalMean, BigDecimal ratio, String explanation) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final FinanceQueries queries;
    private final CondominiumAccess access;
    private final Clock clock;

    public InsightService(NamedParameterJdbcTemplate jdbc, FinanceQueries queries, CondominiumAccess access, Clock clock) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CashProjection cashProjection(UUID condominiumId, int weeks) {
        access.require(condominiumId, Permission.FINANCE_VIEW);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        int horizon = Math.max(1, Math.min(weeks, 26));
        BigDecimal balance = queries.balances(condominiumId, today).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<InsightCalculator.Due> dues = new ArrayList<>();
        BigDecimal[] overdue = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select kind, due_date, amount - paid_amount as remaining from financial_entry
                 where condominium_id = :c and status in ('OPEN', 'PARTIAL') and approval <> 'REJECTED' and due_date <= :until
                """, new MapSqlParameterSource("c", condominiumId).addValue("until", Date.valueOf(today.plusWeeks(horizon))), rs -> {
            boolean incoming = "INCOME".equals(rs.getString("kind"));
            LocalDate due = rs.getDate("due_date").toLocalDate();
            BigDecimal remaining = rs.getBigDecimal("remaining");
            if (due.isBefore(today)) {
                overdue[incoming ? 0 : 1] = overdue[incoming ? 0 : 1].add(remaining);
            } else {
                dues.add(new InsightCalculator.Due(due, remaining, incoming));
            }
        });
        List<InsightCalculator.Week> projection = InsightCalculator.project(balance, dues, today, horizon);
        InsightCalculator.Week lowest = projection.stream().min((a, b) -> a.projectedBalance().compareTo(b.projectedBalance())).orElseThrow();
        List<String> notes = new ArrayList<>();
        notes.add("Projeção pelos vencimentos registrados; supõe que cada título será pago na data de vencimento.");
        notes.add("Títulos vencidos não entram na projeção (a receber vencido: R$ " + overdue[0].toPlainString()
                + "; a pagar vencido: R$ " + overdue[1].toPlainString() + ").");
        if (lowest.projectedBalance().signum() < 0) {
            notes.add("Atenção: saldo projetado negativo na semana de " + lowest.start() + ".");
        }
        return new CashProjection(today, balance, overdue[0], overdue[1], lowest.projectedBalance(), lowest.start(), projection, notes);
    }

    /** Despesas pagas nos últimos {@code months} meses encerrados comparadas aos 6 meses anteriores de cada conta. */
    @Transactional(readOnly = true)
    public List<ExpenseAnomaly> expenseAnomalies(UUID condominiumId, int months) {
        access.require(condominiumId, Permission.FINANCE_VIEW);
        YearMonth last = YearMonth.now(clock.withZone(ZONE)).minusMonths(1);
        int span = Math.max(1, Math.min(months, 6));
        YearMonth first = last.minusMonths(span - 1L + HISTORY_MONTHS);
        Map<UUID, Map<YearMonth, BigDecimal>> byAccount = new HashMap<>();
        Map<UUID, String[]> names = new HashMap<>();
        jdbc.query("""
                select e.chart_account_id as account, a.code, a.name, date_trunc('month', p.paid_on)::date as month, sum(p.total) as total
                  from entry_payment p join financial_entry e on e.id = p.entry_id join chart_account a on a.id = e.chart_account_id
                 where e.condominium_id = :c and e.kind = 'EXPENSE' and p.reversed_at is null and p.paid_on between :from and :to
                 group by e.chart_account_id, a.code, a.name, date_trunc('month', p.paid_on)
                """, new MapSqlParameterSource("c", condominiumId).addValue("from", Date.valueOf(first.atDay(1)))
                .addValue("to", Date.valueOf(last.atEndOfMonth())), rs -> {
            UUID account = rs.getObject("account", UUID.class);
            names.put(account, new String[]{rs.getString("code"), rs.getString("name")});
            byAccount.computeIfAbsent(account, k -> new HashMap<>())
                    .put(YearMonth.from(rs.getDate("month").toLocalDate()), rs.getBigDecimal("total"));
        });
        List<ExpenseAnomaly> result = new ArrayList<>();
        for (var entry : byAccount.entrySet()) {
            for (int i = 0; i < span; i++) {
                YearMonth month = last.minusMonths(i);
                BigDecimal value = entry.getValue().getOrDefault(month, BigDecimal.ZERO);
                List<BigDecimal> history = new ArrayList<>();
                for (int h = 1; h <= HISTORY_MONTHS; h++) {
                    history.add(entry.getValue().getOrDefault(month.minusMonths(h), BigDecimal.ZERO));
                }
                InsightCalculator.Deviation dev = InsightCalculator.deviation(value, history, 3, MIN_ABSOLUTE_DEVIATION);
                if (dev != null) {
                    String[] n = names.get(entry.getKey());
                    result.add(new ExpenseAnomaly(month.toString(), entry.getKey(), n[0], n[1], value, dev.mean(), dev.ratio(),
                            "Pago R$ " + value.toPlainString() + " contra média de R$ " + dev.mean().toPlainString()
                                    + " nos 6 meses anteriores (desvio-padrão R$ " + dev.stdDev().toPlainString() + ")."));
                }
            }
        }
        result.sort((a, b) -> b.month().compareTo(a.month()) != 0 ? b.month().compareTo(a.month()) : a.accountCode().compareTo(b.accountCode()));
        return result;
    }
}
