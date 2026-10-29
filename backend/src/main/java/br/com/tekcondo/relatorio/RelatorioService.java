package br.com.tekcondo.relatorio;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.cobranca.dto.CobrancaDtos;
import br.com.tekcondo.cobranca.service.InadimplenciaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.repository.ContaBancariaRepository;
import br.com.tekcondo.financeiro.service.OrcamentoService;
import br.com.tekcondo.financeiro.service.ConsultasFinanceiras;
import br.com.tekcondo.financeiro.service.TesourariaService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.administradora.repository.AdministradoraRepository;
import br.com.tekcondo.relatorio.RelatorioTabela.Coluna;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.fornecedor.service.ContratoService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relatórios gerenciais e financeiros, calculados sempre a partir dos dados gravados. */
@Service
public class RelatorioService {

    /**
     * Tipos de relatório. A constante (inglês) é o código gravado na auditoria de exportação; a {@code chave}
     * (português, kebab-case) é o identificador público usado na URL e no nome do arquivo exportado.
     */
    public enum Tipo {
        INCOME_EXPENSE("receitas-despesas"),
        BALANCE_SHEET("balancete"),
        CASH_FLOW("fluxo-caixa"),
        DELINQUENCY("inadimplencia"),
        PAYABLES("contas-pagar"),
        RECEIVABLES("contas-receber"),
        PAYMENTS("pagamentos"),
        ALLOCATIONS("rateios"),
        BUDGET("orcamento"),
        BANK_MOVEMENT("movimentacao-bancaria"),
        EXPENSES_BY_SUPPLIER("despesas-por-fornecedor"),
        OCCURRENCES("ocorrencias"),
        CONTRACTS("contratos");

        private final String chave;

        Tipo(String chave) {
            this.chave = chave;
        }

        public String chave() {
            return chave;
        }

        /** Tipo pela chave pública da URL; chave desconhecida responde 400, como qualquer parâmetro de tipo inválido. */
        public static Tipo daChave(String chave) {
            for (Tipo t : values()) {
                if (t.chave.equals(chave)) {
                    return t;
                }
            }
            throw ApiException.requisicaoInvalida("MALFORMED_REQUEST", "Requisição malformada ou com tipos inválidos.");
        }
    }

    public record Parametros(LocalDate de, LocalDate ate, Integer ano, UUID idContaBancaria) {
    }

    public record ArquivoExportado(String nomeArquivo, String tipoConteudo, byte[] conteudo) {
    }

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> SITUACOES = Map.of("OPEN", "Em aberto", "PARTIAL", "Parcial", "PAID", "Pago",
            "CANCELLED", "Cancelado", "RENEGOTIATED", "Renegociado", "GENERATED", "Gerado");

    private final NamedParameterJdbcTemplate jdbc;
    private final AcessoCondominio acesso;
    private final InadimplenciaService inadimplenciaService;
    private final OrcamentoService orcamentoService;
    private final TesourariaService tesourariaService;
    private final ContratoService contratoService;
    private final ConsultasFinanceiras consultas;
    private final ContaBancariaRepository contasBancarias;
    private final AdministradoraRepository administradoras;
    private final RelatorioExporter exporter;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public RelatorioService(NamedParameterJdbcTemplate jdbc, AcessoCondominio acesso, InadimplenciaService inadimplenciaService,
                         OrcamentoService orcamentoService, TesourariaService tesourariaService, ContratoService contratoService, ConsultasFinanceiras consultas,
                         ContaBancariaRepository contasBancarias, AdministradoraRepository administradoras, RelatorioExporter exporter,
                         AuditoriaService auditoria, Clock clock) {
        this.jdbc = jdbc;
        this.acesso = acesso;
        this.inadimplenciaService = inadimplenciaService;
        this.orcamentoService = orcamentoService;
        this.tesourariaService = tesourariaService;
        this.contratoService = contratoService;
        this.consultas = consultas;
        this.contasBancarias = contasBancarias;
        this.administradoras = administradoras;
        this.exporter = exporter;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RelatorioTabela gerar(UUID idCondominio, Tipo tipo, Parametros p) {
        Condominio c = acesso.exigir(idCondominio, Permissao.REPORT_VIEW);
        LocalDate ate = p.ate() == null ? LocalDate.now(clock) : p.ate();
        LocalDate de = p.de() == null ? ate.withDayOfMonth(1) : p.de();
        if (de.isAfter(ate)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "A data inicial deve ser anterior à final.");
        }
        String periodo = c.getNome() + " · " + BR.format(de) + " a " + BR.format(ate);
        var parametros = new MapSqlParameterSource("c", idCondominio).addValue("from", Date.valueOf(de)).addValue("to", Date.valueOf(ate));
        return switch (tipo) {
            case INCOME_EXPENSE -> receitasDespesas(periodo, parametros);
            case BALANCE_SHEET -> balancete(idCondominio, periodo, de, ate);
            case CASH_FLOW -> fluxoCaixa(idCondominio, periodo, de, ate, parametros);
            case DELINQUENCY -> inadimplencia(idCondominio, c.getNome(), ate);
            case PAYABLES -> contas(periodo, parametros, "EXPENSE");
            case RECEIVABLES -> contas(periodo, parametros, "INCOME");
            case PAYMENTS -> pagamentos(periodo, parametros);
            case ALLOCATIONS -> rateios(periodo, parametros);
            case BUDGET -> orcamento(idCondominio, c.getNome(), p.ano() == null ? ate.getYear() : p.ano());
            case BANK_MOVEMENT -> movimentacaoBancaria(idCondominio, periodo, p.idContaBancaria(), de, ate);
            case EXPENSES_BY_SUPPLIER -> despesasPorFornecedor(periodo, parametros);
            case OCCURRENCES -> ocorrencias(periodo, parametros);
            case CONTRACTS -> contratos(idCondominio, c.getNome());
        };
    }

    @Transactional
    public ArquivoExportado exportar(UUID idCondominio, Tipo tipo, Parametros p, RelatorioExporter.Formato formato) {
        Condominio c = acesso.exigir(idCondominio, Permissao.REPORT_EXPORT);
        RelatorioTabela tabela = gerar(idCondominio, tipo, p);
        String nomeAdministradora = administradoras.findById(c.getIdAdministradora()).map(o -> o.getNome()).orElse(null);
        auditoria.registrar(AcaoAuditoria.REPORT_EXPORTED, "Report", idCondominio, Map.of("report", tipo.name(),
                "format", formato.name(), "rows", tabela.linhas().size()));
        String arquivo = tipo.chave() + "-" + LocalDate.now(clock) + "." + formato.extensao;
        return new ArquivoExportado(arquivo, formato.tipoConteudo, exporter.exportar(tabela, formato, nomeAdministradora));
    }

    // ------------------------------------------------------------------ relatórios

    private RelatorioTabela receitasDespesas(String periodo, MapSqlParameterSource parametros) {
        List<List<Object>> linhas = new ArrayList<>();
        BigDecimal[] totais = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select e.kind, a.code, a.name, sum(p.total) total
                  from entry_payment p join financial_entry e on e.id = p.entry_id join chart_account a on a.id = e.chart_account_id
                 where e.condominium_id = :c and p.reversed_at is null and p.paid_on between :from and :to
                 group by e.kind, a.code, a.name order by e.kind desc, a.code
                """, parametros, rs -> {
            boolean receita = "INCOME".equals(rs.getString("kind"));
            BigDecimal total = rs.getBigDecimal("total");
            totais[receita ? 0 : 1] = totais[receita ? 0 : 1].add(total);
            linhas.add(linha(receita ? "Receita" : "Despesa", rs.getString("code"), rs.getString("name"), total));
        });
        return new RelatorioTabela("Receitas e despesas (regime de caixa)", periodo,
                List.of(Coluna.texto("tipo", "Tipo"), Coluna.texto("codigo", "Código"), Coluna.texto("conta", "Conta"), Coluna.moeda("total", "Total")),
                linhas, linha("", "", "Resultado do período", totais[0].subtract(totais[1])),
                List.of("Receitas: " + moeda(totais[0]) + " · Despesas: " + moeda(totais[1]),
                        "Valores pagos/recebidos no período (inclui multa e juros, descontados os abatimentos; baixas estornadas não entram)."));
    }

    private RelatorioTabela balancete(UUID idCondominio, String periodo, LocalDate de, LocalDate ate) {
        List<List<Object>> linhas = new ArrayList<>();
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (ContaBancaria b : contasBancarias.findByIdCondominioOrderByNomeAsc(idCondominio)) {
            BigDecimal saldoInicial = consultas.saldoAntes(b.getId(), b.getSaldoInicial(), de);
            List<FinanceiroDtos.Movimentacao> movimentacoes = consultas.movimentacoes(b.getId(), saldoInicial, de, ate);
            BigDecimal entradas = movimentacoes.stream().map(FinanceiroDtos.Movimentacao::valor).filter(a -> a.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal saidas = movimentacoes.stream().map(FinanceiroDtos.Movimentacao::valor).filter(a -> a.signum() < 0).reduce(BigDecimal.ZERO, BigDecimal::add).negate();
            BigDecimal saldoFinal = saldoInicial.add(entradas).subtract(saidas);
            linhas.add(linha(b.getNome(), saldoInicial, entradas, saidas, saldoFinal));
            t[0] = t[0].add(saldoInicial);
            t[1] = t[1].add(entradas);
            t[2] = t[2].add(saidas);
            t[3] = t[3].add(saldoFinal);
        }
        return new RelatorioTabela("Balancete financeiro", periodo,
                List.of(Coluna.texto("conta", "Conta"), Coluna.moeda("saldoInicial", "Saldo inicial"), Coluna.moeda("entradas", "Entradas"),
                        Coluna.moeda("saidas", "Saídas"), Coluna.moeda("saldoFinal", "Saldo final")),
                linhas, linha("Total", t[0], t[1], t[2], t[3]),
                List.of("Entradas e saídas incluem transferências entre contas do próprio condomínio."));
    }

    private RelatorioTabela fluxoCaixa(UUID idCondominio, String periodo, LocalDate de, LocalDate ate, MapSqlParameterSource parametros) {
        Map<YearMonth, BigDecimal[]> meses = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(de); !m.isAfter(YearMonth.from(ate)); m = m.plusMonths(1)) {
            meses.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        jdbc.query("""
                select date_trunc('month', p.paid_on)::date m, e.kind, sum(p.total) total
                  from entry_payment p join financial_entry e on e.id = p.entry_id
                 where e.condominium_id = :c and p.reversed_at is null and p.paid_on between :from and :to
                 group by 1, 2
                """, parametros, rs -> {
            BigDecimal[] v = meses.get(YearMonth.from(rs.getDate("m").toLocalDate()));
            if (v != null) v["INCOME".equals(rs.getString("kind")) ? 0 : 1] = rs.getBigDecimal("total");
        });
        BigDecimal saldo = contasBancarias.findByIdCondominioOrderByNomeAsc(idCondominio).stream()
                .map(b -> consultas.saldoAntes(b.getId(), b.getSaldoInicial(), de)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoInicialPeriodo = saldo;
        List<List<Object>> linhas = new ArrayList<>();
        BigDecimal totalEntradas = BigDecimal.ZERO;
        BigDecimal totalSaidas = BigDecimal.ZERO;
        for (var e : meses.entrySet()) {
            BigDecimal resultado = e.getValue()[0].subtract(e.getValue()[1]);
            saldo = saldo.add(resultado);
            totalEntradas = totalEntradas.add(e.getValue()[0]);
            totalSaidas = totalSaidas.add(e.getValue()[1]);
            linhas.add(linha(e.getKey().format(DateTimeFormatter.ofPattern("MM/yyyy")), e.getValue()[0], e.getValue()[1], resultado, saldo));
        }
        return new RelatorioTabela("Fluxo de caixa mensal", periodo,
                List.of(Coluna.texto("mes", "Mês"), Coluna.moeda("entradas", "Entradas"), Coluna.moeda("saidas", "Saídas"),
                        Coluna.moeda("resultado", "Resultado"), Coluna.moeda("saldoAcumulado", "Saldo acumulado")),
                linhas, linha("Total", totalEntradas, totalSaidas, totalEntradas.subtract(totalSaidas), saldo),
                List.of("Saldo inicial consolidado das contas: " + moeda(saldoInicialPeriodo) + "."));
    }

    private RelatorioTabela inadimplencia(UUID idCondominio, String nomeCondominio, LocalDate dataBase) {
        CobrancaDtos.Inadimplencia r = inadimplenciaService.inadimplencia(idCondominio, dataBase);
        List<List<Object>> linhas = r.linhas().stream().map(x -> linha(x.rotuloUnidade(), x.nomeResponsavel(), x.quantidadeVencidas(),
                x.vencimentoMaisAntigo(), x.maximoDiasAtraso(), x.principal(), x.encargos(), x.total())).toList();
        return new RelatorioTabela("Inadimplência", nomeCondominio + " · posição em " + BR.format(dataBase),
                List.of(Coluna.texto("unidade", "Unidade"), Coluna.texto("responsavel", "Responsável"), Coluna.numero("quantidade", "Títulos"),
                        Coluna.data("vencimentoMaisAntigo", "Venc. mais antigo"), Coluna.numero("diasAtraso", "Dias de atraso"),
                        Coluna.moeda("principal", "Principal"), Coluna.moeda("encargos", "Multa + juros"), Coluna.moeda("total", "Total atualizado")),
                linhas, linha("Total", "", "", null, "", r.totalPrincipal(), r.totalEncargos(), r.total()),
                List.of("Índice de inadimplência (principal vencido ÷ emitido nos últimos 12 meses): "
                        + r.indiceInadimplencia().toPlainString().replace('.', ',') + "% · " + r.unidadesEmDebito() + " de " + r.unidadesAtivas() + " unidades."));
    }

    private RelatorioTabela contas(String periodo, MapSqlParameterSource parametros, String tipo) {
        List<List<Object>> linhas = new ArrayList<>();
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select e.due_date, e.description, coalesce(s.name, case when un.id is null then null
                         else coalesce(b.name || ' · ', '') || un.identifier end) party,
                       a.code || ' ' || a.name account, e.amount, e.paid_amount, e.status
                  from financial_entry e join chart_account a on a.id = e.chart_account_id
                  left join supplier s on s.id = e.supplier_id left join unit un on un.id = e.unit_id
                  left join building b on b.id = un.building_id
                 where e.condominium_id = :c and e.kind = :kind and e.status not in ('CANCELLED', 'RENEGOTIATED')
                   and e.due_date between :from and :to
                 order by e.due_date, e.description
                """, parametros.addValue("kind", tipo), rs -> {
            BigDecimal valor = rs.getBigDecimal("amount");
            BigDecimal pago = rs.getBigDecimal("paid_amount");
            t[0] = t[0].add(valor);
            t[1] = t[1].add(pago);
            t[2] = t[2].add(valor.subtract(pago));
            linhas.add(linha(rs.getDate("due_date").toLocalDate(), rs.getString("description"), rs.getString("party"),
                    rs.getString("account"), valor, pago, valor.subtract(pago), SITUACOES.getOrDefault(rs.getString("status"), rs.getString("status"))));
        });
        boolean receita = "INCOME".equals(tipo);
        return new RelatorioTabela(receita ? "Contas a receber" : "Contas a pagar", periodo + " (por vencimento)",
                List.of(Coluna.data("vencimento", "Vencimento"), Coluna.texto("descricao", "Descrição"),
                        Coluna.texto("contraparte", receita ? "Unidade" : "Fornecedor"), Coluna.texto("conta", "Conta"),
                        Coluna.moeda("valor", "Valor"), Coluna.moeda("pago", receita ? "Recebido" : "Pago"),
                        Coluna.moeda("aberto", "Em aberto"), Coluna.texto("situacao", "Situação")),
                linhas, linha(null, "Total", "", "", t[0], t[1], t[2], ""), null);
    }

    private RelatorioTabela pagamentos(String periodo, MapSqlParameterSource parametros) {
        List<List<Object>> linhas = new ArrayList<>();
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select p.paid_on, e.kind, e.description, ba.name bank, p.principal, p.fine + p.interest charges, p.discount, p.total
                  from entry_payment p join financial_entry e on e.id = p.entry_id join bank_account ba on ba.id = p.bank_account_id
                 where e.condominium_id = :c and p.reversed_at is null and p.paid_on between :from and :to
                 order by p.paid_on, e.description
                """, parametros, rs -> {
            boolean receita = "INCOME".equals(rs.getString("kind"));
            BigDecimal total = rs.getBigDecimal("total");
            t[receita ? 0 : 1] = t[receita ? 0 : 1].add(total);
            linhas.add(linha(rs.getDate("paid_on").toLocalDate(), receita ? "Recebimento" : "Pagamento", rs.getString("description"),
                    rs.getString("bank"), rs.getBigDecimal("principal"), rs.getBigDecimal("charges"), rs.getBigDecimal("discount"), total));
        });
        return new RelatorioTabela("Pagamentos e recebimentos realizados", periodo,
                List.of(Coluna.data("data", "Data"), Coluna.texto("tipo", "Tipo"), Coluna.texto("descricao", "Descrição"),
                        Coluna.texto("contaBancaria", "Conta"), Coluna.moeda("principal", "Principal"), Coluna.moeda("encargos", "Multa/juros"),
                        Coluna.moeda("desconto", "Desconto"), Coluna.moeda("total", "Total")),
                linhas, null, List.of("Total recebido: " + moeda(t[0]) + " · Total pago: " + moeda(t[1])));
    }

    private RelatorioTabela rateios(String periodo, MapSqlParameterSource parametros) {
        List<List<Object>> linhas = new ArrayList<>();
        jdbc.query("""
                select a.competence, a.description, a.method, a.units_count, a.generated_amount, a.status,
                       coalesce((select sum(e.paid_amount) from financial_entry e where e.allocation_id = a.id), 0) received
                  from allocation a where a.condominium_id = :c and a.competence between date_trunc('month', cast(:from as date)) and :to
                 order by a.competence, a.description
                """, parametros, rs -> {
            linhas.add(linha(YearMonth.from(rs.getDate("competence").toLocalDate()).format(DateTimeFormatter.ofPattern("MM/yyyy")),
                    rs.getString("description"), rotuloCriterio(rs.getString("method")), rs.getInt("units_count"),
                    rs.getBigDecimal("generated_amount"), rs.getBigDecimal("received"),
                    SITUACOES.getOrDefault(rs.getString("status"), rs.getString("status"))));
        });
        return new RelatorioTabela("Rateios e cobranças geradas", periodo,
                List.of(Coluna.texto("competencia", "Competência"), Coluna.texto("descricao", "Descrição"), Coluna.texto("criterio", "Critério"),
                        Coluna.numero("unidades", "Unidades"), Coluna.moeda("gerado", "Gerado"), Coluna.moeda("recebido", "Recebido"),
                        Coluna.texto("situacao", "Situação")), linhas, null, null);
    }

    private RelatorioTabela orcamento(UUID idCondominio, String nomeCondominio, int ano) {
        FinanceiroDtos.Orcamento b = orcamentoService.orcamento(idCondominio, ano);
        List<List<Object>> linhas = new ArrayList<>();
        BigDecimal tb = BigDecimal.ZERO;
        BigDecimal tr = BigDecimal.ZERO;
        for (FinanceiroDtos.ContaOrcamento r : b.contas()) {
            if (r.totalPrevisto().signum() == 0 && r.totalRealizado().signum() == 0) continue;
            BigDecimal execucao = r.totalPrevisto().signum() == 0 ? null
                    : r.totalRealizado().multiply(new BigDecimal("100")).divide(r.totalPrevisto(), 2, java.math.RoundingMode.HALF_UP);
            linhas.add(linha(r.codigo() + " " + r.nome(), r.natureza() == br.com.tekcondo.financeiro.entity.ContaPlano.Natureza.INCOME ? "Receita" : "Despesa",
                    r.totalPrevisto(), r.totalRealizado(), r.totalRealizado().subtract(r.totalPrevisto()), execucao));
        }
        return new RelatorioTabela("Orçamento previsto × realizado " + ano, nomeCondominio,
                List.of(Coluna.texto("conta", "Conta"), Coluna.texto("natureza", "Natureza"), Coluna.moeda("previsto", "Previsto"),
                        Coluna.moeda("realizado", "Realizado"), Coluna.moeda("diferenca", "Diferença"), Coluna.percentual("execucao", "Execução")),
                linhas, null, List.of("Realizado em regime de caixa (pagamentos efetivados no ano)."));
    }

    private RelatorioTabela movimentacaoBancaria(UUID idCondominio, String periodo, UUID idContaBancaria, LocalDate de, LocalDate ate) {
        if (idContaBancaria == null) {
            throw ApiException.requisicaoInvalida("BANK_ACCOUNT_REQUIRED", "Selecione a conta bancária.");
        }
        FinanceiroDtos.Extrato s = tesourariaService.extrato(idCondominio, idContaBancaria, de, ate);
        List<List<Object>> linhas = new ArrayList<>();
        linhas.add(linha(de, "Saldo anterior", null, s.saldoInicial(), ""));
        for (FinanceiroDtos.Movimentacao l : s.movimentacoes()) {
            linhas.add(linha(l.data(), l.descricao(), l.valor(), l.saldo(), l.conciliada() ? "Sim" : "Não"));
        }
        return new RelatorioTabela("Movimentação da conta " + s.nomeContaBancaria(), periodo,
                List.of(Coluna.data("data", "Data"), Coluna.texto("descricao", "Histórico"), Coluna.moeda("valor", "Valor"),
                        Coluna.moeda("saldo", "Saldo"), Coluna.texto("conciliado", "Conciliado")),
                linhas, linha(ate, "Saldo final", null, s.saldoFinal(), ""),
                List.of("Saldo conciliado em " + BR.format(ate) + ": " + moeda(s.saldoConciliado())));
    }

    private RelatorioTabela despesasPorFornecedor(String periodo, MapSqlParameterSource parametros) {
        List<List<Object>> linhas = new ArrayList<>();
        BigDecimal[] total = {BigDecimal.ZERO};
        jdbc.query("""
                select coalesce(s.name, 'Sem fornecedor') supplier, s.document, count(distinct e.id) n, sum(p.total) total
                  from entry_payment p join financial_entry e on e.id = p.entry_id left join supplier s on s.id = e.supplier_id
                 where e.condominium_id = :c and e.kind = 'EXPENSE' and p.reversed_at is null and p.paid_on between :from and :to
                 group by s.name, s.document order by total desc
                """, parametros, rs -> {
            total[0] = total[0].add(rs.getBigDecimal("total"));
            linhas.add(linha(rs.getString("supplier"), rs.getString("document"), rs.getInt("n"), rs.getBigDecimal("total")));
        });
        return new RelatorioTabela("Despesas por fornecedor", periodo,
                List.of(Coluna.texto("fornecedor", "Fornecedor"), Coluna.texto("documento", "CPF/CNPJ"), Coluna.numero("quantidade", "Lançamentos"),
                        Coluna.moeda("total", "Total pago")), linhas, linha("Total", "", "", total[0]), null);
    }

    private RelatorioTabela ocorrencias(String periodo, MapSqlParameterSource parametros) {
        List<List<Object>> linhas = new ArrayList<>();
        jdbc.query("""
                select number, created_at, title, category, priority, status, resolved_at, assignee_name
                  from occurrence where condominium_id = :c and created_at::date between :from and :to order by number
                """, parametros, rs -> {
            linhas.add(linha(rs.getInt("number"), rs.getTimestamp("created_at").toLocalDateTime().toLocalDate(),
                    rs.getString("title"), rs.getString("category"), rs.getString("priority"), rs.getString("status"),
                    rs.getString("assignee_name"), rs.getTimestamp("resolved_at") == null ? null
                            : rs.getTimestamp("resolved_at").toLocalDateTime().toLocalDate()));
        });
        return new RelatorioTabela("Histórico de ocorrências", periodo,
                List.of(Coluna.numero("numero", "Nº"), Coluna.data("abertura", "Abertura"), Coluna.texto("titulo", "Título"),
                        Coluna.texto("categoria", "Categoria"), Coluna.texto("prioridade", "Prioridade"), Coluna.texto("situacao", "Situação"),
                        Coluna.texto("responsavel", "Responsável"), Coluna.data("encerramento", "Encerramento")), linhas, null, null);
    }

    private RelatorioTabela contratos(UUID idCondominio, String nomeCondominio) {
        List<List<Object>> linhas = contratoService.listar(idCondominio).stream().map(c -> linha(c.nomeFornecedor(), c.titulo(), c.valor(),
                c.periodicidade().name(), c.dataInicio(), c.dataFim(), c.situacao() == br.com.tekcondo.fornecedor.entity.Contrato.Situacao.TERMINATED
                        ? "Encerrado" : c.vencido() ? "Vencido" : c.aVencer() ? "A vencer" : "Vigente")).toList();
        return new RelatorioTabela("Contratos", nomeCondominio,
                List.of(Coluna.texto("fornecedor", "Fornecedor"), Coluna.texto("titulo", "Objeto"), Coluna.moeda("valor", "Valor"),
                        Coluna.texto("periodicidade", "Periodicidade"), Coluna.data("inicio", "Início"), Coluna.data("termino", "Término"),
                        Coluna.texto("situacao", "Situação")), linhas, null,
                List.of("\"A vencer\" = término dentro do prazo de aviso prévio do contrato."));
    }

    // ------------------------------------------------------------------

    private static List<Object> linha(Object... valores) {
        return java.util.Arrays.asList(valores);
    }

    private static String moeda(BigDecimal v) {
        return RelatorioExporter.formatar(v, Coluna.moeda("x", "x"));
    }

    private static String rotuloCriterio(String criterio) {
        return switch (criterio) {
            case "IDEAL_FRACTION" -> "Fração ideal";
            case "EQUAL" -> "Igualitário";
            case "FIXED" -> "Valor fixo";
            case "CONSUMPTION" -> "Consumo";
            default -> criterio;
        };
    }
}
