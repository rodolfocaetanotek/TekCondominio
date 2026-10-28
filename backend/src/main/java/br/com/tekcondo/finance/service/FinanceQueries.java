package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.finance.dto.FinanceDtos.StatementLine;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consultas agregadas (somente leitura) sobre as movimentações. Saldos nunca são armazenados: são sempre
 * derivados do saldo inicial + baixas + transferências não estornadas.
 */
@Component
public class FinanceQueries {

    private static final String MOVEMENTS = """
            select 'PAYMENT' as type, p.id, p.paid_on as date, e.description as description,
                   case when e.kind = 'INCOME' then p.total else -p.total end as amount,
                   p.reconciled_at is not null as reconciled, p.created_at as created
              from entry_payment p join financial_entry e on e.id = p.entry_id
             where p.bank_account_id = :account and p.reversed_at is null
            union all
            select 'TRANSFER_IN', t.id, t.transfer_date, coalesce(t.description, 'Transferência recebida'), t.amount,
                   t.reconciled_at is not null, t.created_at
              from bank_transfer t where t.to_account_id = :account and t.reversed_at is null
            union all
            select 'TRANSFER_OUT', t.id, t.transfer_date, coalesce(t.description, 'Transferência enviada'), -t.amount,
                   t.reconciled_at is not null, t.created_at
              from bank_transfer t where t.from_account_id = :account and t.reversed_at is null
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FinanceQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Saldo de cada conta do condomínio na data (inclusive). */
    public Map<UUID, BigDecimal> balances(UUID condominiumId, LocalDate date) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        jdbc.query("""
                select b.id, b.opening_balance + coalesce((
                    select sum(m.amount) from (""" + MOVEMENTS.replace(":account", "b.id") + """
                    ) m where m.date <= :date), 0) as balance
                  from bank_account b where b.condominium_id = :condominium
                """, new MapSqlParameterSource("condominium", condominiumId).addValue("date", Date.valueOf(date)),
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getBigDecimal("balance"));
                });
        return result;
    }

    public BigDecimal balanceBefore(UUID accountId, BigDecimal openingBalance, LocalDate exclusiveDate) {
        BigDecimal sum = jdbc.queryForObject("select coalesce(sum(amount), 0) from (" + MOVEMENTS + ") m where m.date < :date",
                new MapSqlParameterSource("account", accountId).addValue("date", Date.valueOf(exclusiveDate)), BigDecimal.class);
        return openingBalance.add(sum == null ? BigDecimal.ZERO : sum);
    }

    /** Movimentações do período com saldo acumulado. */
    public List<StatementLine> movements(UUID accountId, BigDecimal startBalance, LocalDate from, LocalDate to) {
        List<StatementLine> lines = new ArrayList<>();
        BigDecimal[] running = {startBalance};
        jdbc.query("select * from (" + MOVEMENTS + ") m where m.date between :from and :to order by m.date, m.created",
                new MapSqlParameterSource("account", accountId).addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to)),
                rs -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    running[0] = running[0].add(amount);
                    lines.add(new StatementLine(rs.getString("type"), rs.getObject("id", UUID.class),
                            rs.getDate("date").toLocalDate(), rs.getString("description"), amount, running[0],
                            rs.getBoolean("reconciled")));
                });
        return lines;
    }

    public BigDecimal reconciledBalance(UUID accountId, BigDecimal openingBalance, LocalDate to) {
        BigDecimal sum = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from (" + MOVEMENTS + ") m where m.date <= :to and m.reconciled",
                new MapSqlParameterSource("account", accountId).addValue("to", Date.valueOf(to)), BigDecimal.class);
        return openingBalance.add(sum == null ? BigDecimal.ZERO : sum);
    }

    /**
     * Realizado (regime de caixa) por conta do plano e mês: soma das baixas não estornadas (principal + encargos
     * - desconto), pela data de pagamento.
     */
    public Map<UUID, BigDecimal[]> realizedByAccountAndMonth(UUID condominiumId, int year) {
        Map<UUID, BigDecimal[]> result = new HashMap<>();
        jdbc.query("""
                select e.chart_account_id as account, extract(month from p.paid_on)::int as month, sum(p.total) as total
                  from entry_payment p join financial_entry e on e.id = p.entry_id
                 where e.condominium_id = :condominium and p.reversed_at is null and extract(year from p.paid_on) = :year
                 group by e.chart_account_id, extract(month from p.paid_on)
                """, new MapSqlParameterSource("condominium", condominiumId).addValue("year", year), rs -> {
            BigDecimal[] months = result.computeIfAbsent(rs.getObject("account", UUID.class), k -> zeros());
            months[rs.getInt("month") - 1] = rs.getBigDecimal("total");
        });
        return result;
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] months = new BigDecimal[12];
        java.util.Arrays.fill(months, BigDecimal.ZERO);
        return months;
    }
}
