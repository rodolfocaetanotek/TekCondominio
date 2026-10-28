package br.com.tekcondo.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.condominium.dto.CondominiumOption;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.service.CondominiumService;
import br.com.tekcondo.finance.service.FinanceQueries;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.exception.ApiException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores do painel. Todos os números vêm de consultas sobre os dados gravados, restritos aos condomínios em que
 * o usuário tem a permissão correspondente (financeiro exige FINANCE_VIEW; ocorrências, OCCURRENCE_VIEW).
 */
@Service
public class DashboardService {

    public record MonthPoint(String month, BigDecimal income, BigDecimal expense) {
    }

    public record DueItem(UUID id, UUID condominiumId, String condominiumName, String description, LocalDate dueDate,
                          BigDecimal amount, String approval) {
    }

    public record RecentItem(UUID id, UUID condominiumId, String condominiumName, String title, String status, String when) {
    }

    public record Dashboard(LocalDate from, LocalDate to, long condominiums, long units, long residents, boolean finance,
                            BigDecimal income, BigDecimal expense, BigDecimal balance, BigDecimal receivablesOpen,
                            BigDecimal payablesOpen, BigDecimal overdueAmount, long overdueCharges, long chargesIssued,
                            BigDecimal delinquencyRate, List<MonthPoint> series, List<DueItem> payablesDueSoon,
                            List<RecentItem> recentOccurrences, List<RecentItem> recentAnnouncements) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final CondominiumService condominiums;
    private final FinanceQueries queries;
    private final Clock clock;

    public DashboardService(NamedParameterJdbcTemplate jdbc, CondominiumService condominiums, FinanceQueries queries, Clock clock) {
        this.jdbc = jdbc;
        this.condominiums = condominiums;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(UUID condominiumId, LocalDate fromParam, LocalDate toParam) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = toParam == null ? today : toParam;
        LocalDate from = fromParam == null ? to.withDayOfMonth(1) : fromParam;
        List<CondominiumOption> options = condominiums.accessible();
        if (condominiumId != null) {
            options = options.stream().filter(o -> o.id().equals(condominiumId)).toList();
            if (options.isEmpty()) {
                throw ApiException.notFound("Condomínio");
            }
        }
        Map<UUID, String> names = options.stream().collect(Collectors.toMap(CondominiumOption::id, CondominiumOption::name));
        Set<UUID> all = names.keySet();
        Set<UUID> fin = ids(options, Permission.FINANCE_VIEW);
        Set<UUID> occ = ids(options, Permission.OCCURRENCE_VIEW);
        Set<UUID> ann = ids(options, Permission.ANNOUNCEMENT_VIEW);

        long condoCount = options.stream().filter(o -> o.status() == CondominiumStatus.ACTIVE).count();
        long units = count("select count(*) from unit where active and condominium_id in (:ids)", all);
        long residents = count("""
                select count(distinct o.person_id) from unit_occupancy o join unit u on u.id = o.unit_id
                 where o.end_date is null and u.condominium_id in (:ids)""", all);

        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO, balance = BigDecimal.ZERO, recOpen = BigDecimal.ZERO,
                payOpen = BigDecimal.ZERO, overdue = BigDecimal.ZERO, rate = BigDecimal.ZERO;
        long overdueCount = 0, issued = 0;
        List<MonthPoint> series = new ArrayList<>();
        List<DueItem> dueSoon = new ArrayList<>();
        if (!fin.isEmpty()) {
            var p = new MapSqlParameterSource("ids", fin).addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to))
                    .addValue("today", Date.valueOf(today));
            Map<String, Object> cash = jdbc.queryForMap("""
                    select coalesce(sum(case when e.kind = 'INCOME' then p.total end), 0) income,
                           coalesce(sum(case when e.kind = 'EXPENSE' then p.total end), 0) expense
                      from entry_payment p join financial_entry e on e.id = p.entry_id
                     where e.condominium_id in (:ids) and p.reversed_at is null and p.paid_on between :from and :to""", p);
            income = (BigDecimal) cash.get("income");
            expense = (BigDecimal) cash.get("expense");
            Map<String, Object> open = jdbc.queryForMap("""
                    select coalesce(sum(case when kind = 'INCOME' then amount - paid_amount end), 0) rec,
                           coalesce(sum(case when kind = 'EXPENSE' then amount - paid_amount end), 0) pay,
                           coalesce(sum(case when kind = 'INCOME' and unit_id is not null and due_date < :today then amount - paid_amount end), 0) overdue,
                           count(case when kind = 'INCOME' and unit_id is not null and due_date < :today then 1 end) overdue_count
                      from financial_entry where condominium_id in (:ids) and status in ('OPEN', 'PARTIAL')""", p);
            recOpen = (BigDecimal) open.get("rec");
            payOpen = (BigDecimal) open.get("pay");
            overdue = (BigDecimal) open.get("overdue");
            overdueCount = ((Number) open.get("overdue_count")).longValue();
            issued = count0("""
                    select count(*) from financial_entry where condominium_id in (:ids) and kind = 'INCOME' and unit_id is not null
                       and status <> 'CANCELLED' and origin <> 'AGREEMENT' and competence between date_trunc('month', cast(:from as date)) and :to""", p);
            BigDecimal issued12 = jdbc.queryForObject("""
                    select coalesce(sum(amount), 0) from financial_entry where condominium_id in (:ids) and kind = 'INCOME'
                       and unit_id is not null and status <> 'CANCELLED' and origin <> 'AGREEMENT'
                       and due_date between cast(:today as date) - interval '12 months' and :today""", p, BigDecimal.class);
            rate = issued12 == null || issued12.signum() == 0 ? BigDecimal.ZERO
                    : overdue.multiply(new BigDecimal("100")).divide(issued12, 2, RoundingMode.HALF_UP);
            for (UUID id : fin) {
                balance = balance.add(queries.balances(id, today).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            Map<YearMonth, BigDecimal[]> months = new LinkedHashMap<>();
            YearMonth last = YearMonth.from(to);
            for (YearMonth m = last.minusMonths(11); !m.isAfter(last); m = m.plusMonths(1)) {
                months.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }
            jdbc.query("""
                    select date_trunc('month', p.paid_on)::date m, e.kind, sum(p.total) total
                      from entry_payment p join financial_entry e on e.id = p.entry_id
                     where e.condominium_id in (:ids) and p.reversed_at is null
                       and p.paid_on >= :start and p.paid_on <= :to group by 1, 2""",
                    new MapSqlParameterSource(p.getValues()).addValue("start", Date.valueOf(last.minusMonths(11).atDay(1))), rs -> {
                        BigDecimal[] v = months.get(YearMonth.from(rs.getDate("m").toLocalDate()));
                        if (v != null) v["INCOME".equals(rs.getString("kind")) ? 0 : 1] = rs.getBigDecimal("total");
                    });
            months.forEach((m, v) -> series.add(new MonthPoint(m.toString(), v[0], v[1])));
            jdbc.query("""
                    select id, condominium_id, description, due_date, amount - paid_amount open, approval
                      from financial_entry where condominium_id in (:ids) and kind = 'EXPENSE' and status in ('OPEN', 'PARTIAL')
                       and due_date <= cast(:today as date) + 7 order by due_date limit 8""", p, rs -> {
                UUID c = rs.getObject("condominium_id", UUID.class);
                dueSoon.add(new DueItem(rs.getObject("id", UUID.class), c, names.get(c), rs.getString("description"),
                        rs.getDate("due_date").toLocalDate(), rs.getBigDecimal("open"), rs.getString("approval")));
            });
        }
        List<RecentItem> occurrences = new ArrayList<>();
        if (!occ.isEmpty()) {
            jdbc.query("""
                    select id, condominium_id, number, title, status, created_at from occurrence
                     where condominium_id in (:ids) and status in ('OPEN', 'IN_PROGRESS', 'WAITING') order by created_at desc limit 5""",
                    new MapSqlParameterSource("ids", occ), rs -> {
                        UUID c = rs.getObject("condominium_id", UUID.class);
                        occurrences.add(new RecentItem(rs.getObject("id", UUID.class), c, names.get(c), "Nº " + rs.getInt("number")
                                + " · " + rs.getString("title"), rs.getString("status"), rs.getTimestamp("created_at").toInstant().toString()));
                    });
        }
        List<RecentItem> announcements = new ArrayList<>();
        if (!ann.isEmpty()) {
            jdbc.query("""
                    select id, condominium_id, title, published_at from announcement
                     where condominium_id in (:ids) and archived_at is null order by published_at desc limit 5""",
                    new MapSqlParameterSource("ids", ann), rs -> {
                        UUID c = rs.getObject("condominium_id", UUID.class);
                        announcements.add(new RecentItem(rs.getObject("id", UUID.class), c, names.get(c), rs.getString("title"),
                                null, rs.getTimestamp("published_at").toInstant().toString()));
                    });
        }
        return new Dashboard(from, to, condoCount, units, residents, !fin.isEmpty(), income, expense, balance, recOpen, payOpen,
                overdue, overdueCount, issued, rate, series, dueSoon, occurrences, announcements);
    }

    private static Set<UUID> ids(List<CondominiumOption> options, Permission permission) {
        return options.stream().filter(o -> o.permissions().contains(permission)).map(CondominiumOption::id).collect(Collectors.toSet());
    }

    private long count(String sql, Set<UUID> ids) {
        return ids.isEmpty() ? 0 : count0(sql, new MapSqlParameterSource("ids", ids));
    }

    private long count0(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n;
    }
}
