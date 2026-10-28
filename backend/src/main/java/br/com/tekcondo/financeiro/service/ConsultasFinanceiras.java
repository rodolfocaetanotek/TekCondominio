package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.financeiro.dto.FinanceiroDtos.Movimentacao;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consultas agregadas (somente leitura) sobre as movimentações. Saldos nunca são armazenados: são sempre
 * derivados do saldo inicial + baixas + transferências não estornadas.
 */
@Component
public class ConsultasFinanceiras {

    private static final String MOVIMENTACOES = """
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

    public ConsultasFinanceiras(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Saldo de cada conta do condomínio na data (inclusive). */
    public Map<UUID, BigDecimal> saldos(UUID idCondominio, LocalDate data) {
        Map<UUID, BigDecimal> resultado = new HashMap<>();
        jdbc.query("""
                select b.id, b.opening_balance + coalesce((
                    select sum(m.amount) from (""" + MOVIMENTACOES.replace(":account", "b.id") + """
                    ) m where m.date <= :date), 0) as balance
                  from bank_account b where b.condominium_id = :condominium
                """, new MapSqlParameterSource("condominium", idCondominio).addValue("date", Date.valueOf(data)),
                rs -> {
                    resultado.put(rs.getObject("id", UUID.class), rs.getBigDecimal("balance"));
                });
        return resultado;
    }

    public BigDecimal saldoAntes(UUID idConta, BigDecimal saldoInicial, LocalDate dataExclusiva) {
        BigDecimal soma = jdbc.queryForObject("select coalesce(sum(amount), 0) from (" + MOVIMENTACOES + ") m where m.date < :date",
                new MapSqlParameterSource("account", idConta).addValue("date", Date.valueOf(dataExclusiva)), BigDecimal.class);
        return saldoInicial.add(soma == null ? BigDecimal.ZERO : soma);
    }

    /** Movimentações do período com saldo acumulado. */
    public List<Movimentacao> movimentacoes(UUID idConta, BigDecimal saldoInicial, LocalDate de, LocalDate ate) {
        List<Movimentacao> linhas = new ArrayList<>();
        BigDecimal[] acumulado = {saldoInicial};
        jdbc.query("select * from (" + MOVIMENTACOES + ") m where m.date between :from and :to order by m.date, m.created",
                new MapSqlParameterSource("account", idConta).addValue("from", Date.valueOf(de)).addValue("to", Date.valueOf(ate)),
                rs -> {
                    BigDecimal valor = rs.getBigDecimal("amount");
                    acumulado[0] = acumulado[0].add(valor);
                    linhas.add(new Movimentacao(rs.getString("type"), rs.getObject("id", UUID.class),
                            rs.getDate("date").toLocalDate(), rs.getString("description"), valor, acumulado[0],
                            rs.getBoolean("reconciled")));
                });
        return linhas;
    }

    public BigDecimal saldoConciliado(UUID idConta, BigDecimal saldoInicial, LocalDate ate) {
        BigDecimal soma = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from (" + MOVIMENTACOES + ") m where m.date <= :to and m.reconciled",
                new MapSqlParameterSource("account", idConta).addValue("to", Date.valueOf(ate)), BigDecimal.class);
        return saldoInicial.add(soma == null ? BigDecimal.ZERO : soma);
    }

    /**
     * Realizado (regime de caixa) por conta do plano e mês: soma das baixas não estornadas (principal + encargos
     * - desconto), pela data de pagamento.
     */
    public Map<UUID, BigDecimal[]> realizadoPorContaEMes(UUID idCondominio, int ano) {
        Map<UUID, BigDecimal[]> resultado = new HashMap<>();
        jdbc.query("""
                select e.chart_account_id as account, extract(month from p.paid_on)::int as month, sum(p.total) as total
                  from entry_payment p join financial_entry e on e.id = p.entry_id
                 where e.condominium_id = :condominium and p.reversed_at is null and extract(year from p.paid_on) = :year
                 group by e.chart_account_id, extract(month from p.paid_on)
                """, new MapSqlParameterSource("condominium", idCondominio).addValue("year", ano), rs -> {
            BigDecimal[] meses = resultado.computeIfAbsent(rs.getObject("account", UUID.class), k -> zeros());
            meses[rs.getInt("month") - 1] = rs.getBigDecimal("total");
        });
        return resultado;
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] meses = new BigDecimal[12];
        java.util.Arrays.fill(meses, BigDecimal.ZERO);
        return meses;
    }
}
