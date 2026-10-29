package br.com.tekcondo.painel;

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

import br.com.tekcondo.condominio.dto.OpcaoCondominio;
import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.condominio.service.CondominioService;
import br.com.tekcondo.financeiro.service.ConsultasFinanceiras;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores do painel. Todos os números vêm de consultas sobre os dados gravados, restritos aos condomínios em que
 * o usuário tem a permissão correspondente (financeiro exige FINANCE_VIEW; ocorrências, OCCURRENCE_VIEW).
 */
@Service
public class PainelService {

    public record PontoMensal(String mes, BigDecimal receitas, BigDecimal despesas) {
    }

    public record ContaAPagar(UUID id, UUID idCondominio, String nomeCondominio, String descricao, LocalDate dataVencimento,
                          BigDecimal valor, String aprovacao) {
    }

    public record ItemRecente(UUID id, UUID idCondominio, String nomeCondominio, String titulo, String situacao, String quando) {
    }

    public record Painel(LocalDate de, LocalDate ate, long condominios, long unidades, long moradores, boolean financeiro,
                            BigDecimal receitas, BigDecimal despesas, BigDecimal saldo, BigDecimal aReceberAberto,
                            BigDecimal aPagarAberto, BigDecimal valorVencido, long cobrancasVencidas, long cobrancasEmitidas,
                            BigDecimal indiceInadimplencia, List<PontoMensal> serie, List<ContaAPagar> contasAPagarProximas,
                            List<ItemRecente> ocorrenciasRecentes, List<ItemRecente> comunicadosRecentes) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final CondominioService condominios;
    private final ConsultasFinanceiras consultas;
    private final Clock clock;

    public PainelService(NamedParameterJdbcTemplate jdbc, CondominioService condominios, ConsultasFinanceiras consultas, Clock clock) {
        this.jdbc = jdbc;
        this.condominios = condominios;
        this.consultas = consultas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Painel painel(UUID idCondominio, LocalDate deParam, LocalDate ateParam) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate ate = ateParam == null ? hoje : ateParam;
        LocalDate de = deParam == null ? ate.withDayOfMonth(1) : deParam;
        List<OpcaoCondominio> opcoes = condominios.acessiveis();
        if (idCondominio != null) {
            opcoes = opcoes.stream().filter(o -> o.id().equals(idCondominio)).toList();
            if (opcoes.isEmpty()) {
                throw ApiException.naoEncontrado("Condomínio");
            }
        }
        Map<UUID, String> nomes = opcoes.stream().collect(Collectors.toMap(OpcaoCondominio::id, OpcaoCondominio::nome));
        Set<UUID> todos = nomes.keySet();
        Set<UUID> comFinanceiro = idsComPermissao(opcoes, Permissao.FINANCE_VIEW);
        Set<UUID> comOcorrencias = idsComPermissao(opcoes, Permissao.OCCURRENCE_VIEW);
        Set<UUID> comComunicados = idsComPermissao(opcoes, Permissao.ANNOUNCEMENT_VIEW);

        long quantidadeCondominios = opcoes.stream().filter(o -> o.situacao() == SituacaoCondominio.ACTIVE).count();
        long unidades = contar("select count(*) from unit where active and condominium_id in (:ids)", todos);
        long moradores = contar("""
                select count(distinct o.person_id) from unit_occupancy o join unit u on u.id = o.unit_id
                 where o.end_date is null and u.condominium_id in (:ids)""", todos);

        BigDecimal receitas = BigDecimal.ZERO, despesas = BigDecimal.ZERO, saldo = BigDecimal.ZERO, aReceber = BigDecimal.ZERO,
                aPagar = BigDecimal.ZERO, vencido = BigDecimal.ZERO, indice = BigDecimal.ZERO;
        long quantidadeVencidas = 0, emitidas = 0;
        List<PontoMensal> serie = new ArrayList<>();
        List<ContaAPagar> aPagarProximas = new ArrayList<>();
        if (!comFinanceiro.isEmpty()) {
            var p = new MapSqlParameterSource("ids", comFinanceiro).addValue("from", Date.valueOf(de)).addValue("to", Date.valueOf(ate))
                    .addValue("today", Date.valueOf(hoje));
            Map<String, Object> caixa = jdbc.queryForMap("""
                    select coalesce(sum(case when e.kind = 'INCOME' then p.total end), 0) income,
                           coalesce(sum(case when e.kind = 'EXPENSE' then p.total end), 0) expense
                      from entry_payment p join financial_entry e on e.id = p.entry_id
                     where e.condominium_id in (:ids) and p.reversed_at is null and p.paid_on between :from and :to""", p);
            receitas = (BigDecimal) caixa.get("income");
            despesas = (BigDecimal) caixa.get("expense");
            Map<String, Object> aberto = jdbc.queryForMap("""
                    select coalesce(sum(case when kind = 'INCOME' then amount - paid_amount end), 0) rec,
                           coalesce(sum(case when kind = 'EXPENSE' then amount - paid_amount end), 0) pay,
                           coalesce(sum(case when kind = 'INCOME' and unit_id is not null and due_date < :today then amount - paid_amount end), 0) overdue,
                           count(case when kind = 'INCOME' and unit_id is not null and due_date < :today then 1 end) overdue_count
                      from financial_entry where condominium_id in (:ids) and status in ('OPEN', 'PARTIAL')""", p);
            aReceber = (BigDecimal) aberto.get("rec");
            aPagar = (BigDecimal) aberto.get("pay");
            vencido = (BigDecimal) aberto.get("overdue");
            quantidadeVencidas = ((Number) aberto.get("overdue_count")).longValue();
            emitidas = contarComParametros("""
                    select count(*) from financial_entry where condominium_id in (:ids) and kind = 'INCOME' and unit_id is not null
                       and status <> 'CANCELLED' and origin <> 'AGREEMENT' and competence between date_trunc('month', cast(:from as date)) and :to""", p);
            BigDecimal emitidoUltimos12Meses = jdbc.queryForObject("""
                    select coalesce(sum(amount), 0) from financial_entry where condominium_id in (:ids) and kind = 'INCOME'
                       and unit_id is not null and status <> 'CANCELLED' and origin <> 'AGREEMENT'
                       and due_date between cast(:today as date) - interval '12 months' and :today""", p, BigDecimal.class);
            indice = emitidoUltimos12Meses == null || emitidoUltimos12Meses.signum() == 0 ? BigDecimal.ZERO
                    : vencido.multiply(new BigDecimal("100")).divide(emitidoUltimos12Meses, 2, RoundingMode.HALF_UP);
            for (UUID id : comFinanceiro) {
                saldo = saldo.add(consultas.saldos(id, hoje).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            Map<YearMonth, BigDecimal[]> meses = new LinkedHashMap<>();
            YearMonth ultimo = YearMonth.from(ate);
            for (YearMonth m = ultimo.minusMonths(11); !m.isAfter(ultimo); m = m.plusMonths(1)) {
                meses.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }
            jdbc.query("""
                    select date_trunc('month', p.paid_on)::date m, e.kind, sum(p.total) total
                      from entry_payment p join financial_entry e on e.id = p.entry_id
                     where e.condominium_id in (:ids) and p.reversed_at is null
                       and p.paid_on >= :start and p.paid_on <= :to group by 1, 2""",
                    new MapSqlParameterSource(p.getValues()).addValue("start", Date.valueOf(ultimo.minusMonths(11).atDay(1))), rs -> {
                        BigDecimal[] v = meses.get(YearMonth.from(rs.getDate("m").toLocalDate()));
                        if (v != null) v["INCOME".equals(rs.getString("kind")) ? 0 : 1] = rs.getBigDecimal("total");
                    });
            meses.forEach((m, v) -> serie.add(new PontoMensal(m.toString(), v[0], v[1])));
            jdbc.query("""
                    select id, condominium_id, description, due_date, amount - paid_amount open, approval
                      from financial_entry where condominium_id in (:ids) and kind = 'EXPENSE' and status in ('OPEN', 'PARTIAL')
                       and due_date <= cast(:today as date) + 7 order by due_date limit 8""", p, rs -> {
                UUID c = rs.getObject("condominium_id", UUID.class);
                aPagarProximas.add(new ContaAPagar(rs.getObject("id", UUID.class), c, nomes.get(c), rs.getString("description"),
                        rs.getDate("due_date").toLocalDate(), rs.getBigDecimal("open"), rs.getString("approval")));
            });
        }
        List<ItemRecente> ocorrencias = new ArrayList<>();
        if (!comOcorrencias.isEmpty()) {
            jdbc.query("""
                    select id, condominium_id, number, title, status, created_at from occurrence
                     where condominium_id in (:ids) and status in ('OPEN', 'IN_PROGRESS', 'WAITING') order by created_at desc limit 5""",
                    new MapSqlParameterSource("ids", comOcorrencias), rs -> {
                        UUID c = rs.getObject("condominium_id", UUID.class);
                        ocorrencias.add(new ItemRecente(rs.getObject("id", UUID.class), c, nomes.get(c), "Nº " + rs.getInt("number")
                                + " · " + rs.getString("title"), rs.getString("status"), rs.getTimestamp("created_at").toInstant().toString()));
                    });
        }
        List<ItemRecente> comunicados = new ArrayList<>();
        if (!comComunicados.isEmpty()) {
            jdbc.query("""
                    select id, condominium_id, title, published_at from announcement
                     where condominium_id in (:ids) and archived_at is null order by published_at desc limit 5""",
                    new MapSqlParameterSource("ids", comComunicados), rs -> {
                        UUID c = rs.getObject("condominium_id", UUID.class);
                        comunicados.add(new ItemRecente(rs.getObject("id", UUID.class), c, nomes.get(c), rs.getString("title"),
                                null, rs.getTimestamp("published_at").toInstant().toString()));
                    });
        }
        return new Painel(de, ate, quantidadeCondominios, unidades, moradores, !comFinanceiro.isEmpty(), receitas, despesas, saldo, aReceber, aPagar,
                vencido, quantidadeVencidas, emitidas, indice, serie, aPagarProximas, ocorrencias, comunicados);
    }

    private static Set<UUID> idsComPermissao(List<OpcaoCondominio> opcoes, Permissao permissao) {
        return opcoes.stream().filter(o -> o.permissoes().contains(permissao)).map(OpcaoCondominio::id).collect(Collectors.toSet());
    }

    private long contar(String sql, Set<UUID> ids) {
        return ids.isEmpty() ? 0 : contarComParametros(sql, new MapSqlParameterSource("ids", ids));
    }

    private long contarComParametros(String sql, MapSqlParameterSource parametros) {
        Long n = jdbc.queryForObject(sql, parametros, Long.class);
        return n == null ? 0 : n;
    }
}
