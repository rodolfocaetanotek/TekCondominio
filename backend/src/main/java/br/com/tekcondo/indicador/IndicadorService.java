package br.com.tekcondo.indicador;

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

import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.service.ConsultasFinanceiras;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores para decisão, calculados SOMENTE a partir dos dados registrados (sem estimativas inventadas):
 * projeção de caixa pelos vencimentos em aberto e despesas fora do padrão histórico de cada conta.
 */
@Service
public class IndicadorService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final int MESES_HISTORICO = 6;
    private static final BigDecimal DESVIO_MINIMO_ABSOLUTO = new BigDecimal("100.00");

    public record ProjecaoCaixa(LocalDate dataBase, BigDecimal saldoAtual, BigDecimal aReceberVencido, BigDecimal aPagarVencido,
                                BigDecimal menorSaldoProjetado, LocalDate dataMenorSaldo, List<IndicadorCalculator.Semana> semanas,
                                List<String> observacoes) {
    }

    public record DespesaForaDoPadrao(String mes, UUID idContaPlano, String codigoConta, String nomeConta, BigDecimal valor,
                                      BigDecimal mediaHistorica, BigDecimal razao, String explicacao) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ConsultasFinanceiras consultas;
    private final AcessoCondominio acesso;
    private final Clock clock;

    public IndicadorService(NamedParameterJdbcTemplate jdbc, ConsultasFinanceiras consultas, AcessoCondominio acesso, Clock clock) {
        this.jdbc = jdbc;
        this.consultas = consultas;
        this.acesso = acesso;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProjecaoCaixa projecaoCaixa(UUID idCondominio, int semanas) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        LocalDate hoje = LocalDate.now(clock.withZone(ZONA));
        int horizonte = Math.max(1, Math.min(semanas, 26));
        BigDecimal saldo = consultas.saldos(idCondominio, hoje).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<IndicadorCalculator.Vencimento> vencimentos = new ArrayList<>();
        BigDecimal[] vencido = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select kind, due_date, amount - paid_amount as remaining from financial_entry
                 where condominium_id = :c and status in ('OPEN', 'PARTIAL') and approval <> 'REJECTED' and due_date <= :until
                """, new MapSqlParameterSource("c", idCondominio).addValue("until", Date.valueOf(hoje.plusWeeks(horizonte))), rs -> {
            boolean aReceber = "INCOME".equals(rs.getString("kind"));
            LocalDate dataVencimento = rs.getDate("due_date").toLocalDate();
            BigDecimal valorAberto = rs.getBigDecimal("remaining");
            if (dataVencimento.isBefore(hoje)) {
                vencido[aReceber ? 0 : 1] = vencido[aReceber ? 0 : 1].add(valorAberto);
            } else {
                vencimentos.add(new IndicadorCalculator.Vencimento(dataVencimento, valorAberto, aReceber));
            }
        });
        List<IndicadorCalculator.Semana> projecao = IndicadorCalculator.projetar(saldo, vencimentos, hoje, horizonte);
        IndicadorCalculator.Semana menor = projecao.stream()
                .min((a, b) -> a.saldoProjetado().compareTo(b.saldoProjetado())).orElseThrow();
        List<String> observacoes = new ArrayList<>();
        observacoes.add("Projeção pelos vencimentos registrados; supõe que cada título será pago na data de vencimento.");
        observacoes.add("Títulos vencidos não entram na projeção (a receber vencido: R$ " + vencido[0].toPlainString()
                + "; a pagar vencido: R$ " + vencido[1].toPlainString() + ").");
        if (menor.saldoProjetado().signum() < 0) {
            observacoes.add("Atenção: saldo projetado negativo na semana de " + menor.inicio() + ".");
        }
        return new ProjecaoCaixa(hoje, saldo, vencido[0], vencido[1], menor.saldoProjetado(), menor.inicio(), projecao, observacoes);
    }

    /** Despesas pagas nos últimos {@code meses} meses encerrados comparadas aos 6 meses anteriores de cada conta. */
    @Transactional(readOnly = true)
    public List<DespesaForaDoPadrao> despesasForaDoPadrao(UUID idCondominio, int meses) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        YearMonth ultimo = YearMonth.now(clock.withZone(ZONA)).minusMonths(1);
        int janela = Math.max(1, Math.min(meses, 6));
        YearMonth primeiro = ultimo.minusMonths(janela - 1L + MESES_HISTORICO);
        Map<UUID, Map<YearMonth, BigDecimal>> porConta = new HashMap<>();
        Map<UUID, String[]> nomes = new HashMap<>();
        jdbc.query("""
                select e.chart_account_id as account, a.code, a.name, date_trunc('month', p.paid_on)::date as month, sum(p.total) as total
                  from entry_payment p join financial_entry e on e.id = p.entry_id join chart_account a on a.id = e.chart_account_id
                 where e.condominium_id = :c and e.kind = 'EXPENSE' and p.reversed_at is null and p.paid_on between :from and :to
                 group by e.chart_account_id, a.code, a.name, date_trunc('month', p.paid_on)
                """, new MapSqlParameterSource("c", idCondominio).addValue("from", Date.valueOf(primeiro.atDay(1)))
                .addValue("to", Date.valueOf(ultimo.atEndOfMonth())), rs -> {
            UUID conta = rs.getObject("account", UUID.class);
            nomes.put(conta, new String[]{rs.getString("code"), rs.getString("name")});
            porConta.computeIfAbsent(conta, k -> new HashMap<>())
                    .put(YearMonth.from(rs.getDate("month").toLocalDate()), rs.getBigDecimal("total"));
        });
        List<DespesaForaDoPadrao> resultado = new ArrayList<>();
        for (var entrada : porConta.entrySet()) {
            for (int i = 0; i < janela; i++) {
                YearMonth mes = ultimo.minusMonths(i);
                BigDecimal valor = entrada.getValue().getOrDefault(mes, BigDecimal.ZERO);
                List<BigDecimal> historico = new ArrayList<>();
                for (int h = 1; h <= MESES_HISTORICO; h++) {
                    historico.add(entrada.getValue().getOrDefault(mes.minusMonths(h), BigDecimal.ZERO));
                }
                IndicadorCalculator.Desvio desvio = IndicadorCalculator.desvio(valor, historico, 3, DESVIO_MINIMO_ABSOLUTO);
                if (desvio != null) {
                    String[] n = nomes.get(entrada.getKey());
                    resultado.add(new DespesaForaDoPadrao(mes.toString(), entrada.getKey(), n[0], n[1], valor, desvio.media(),
                            desvio.razao(), "Pago R$ " + valor.toPlainString() + " contra média de R$ " + desvio.media().toPlainString()
                                    + " nos 6 meses anteriores (desvio-padrão R$ " + desvio.desvioPadrao().toPlainString() + ")."));
                }
            }
        }
        resultado.sort((a, b) -> b.mes().compareTo(a.mes()) != 0 ? b.mes().compareTo(a.mes())
                : a.codigoConta().compareTo(b.codigoConta()));
        return resultado;
    }
}
