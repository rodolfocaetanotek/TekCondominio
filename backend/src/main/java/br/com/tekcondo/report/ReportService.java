package br.com.tekcondo.report;

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

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.billing.dto.BillingDtos;
import br.com.tekcondo.billing.service.CollectionService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.dto.FinanceDtos;
import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.repository.BankAccountRepository;
import br.com.tekcondo.finance.service.BudgetService;
import br.com.tekcondo.finance.service.FinanceQueries;
import br.com.tekcondo.finance.service.TreasuryService;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.organization.repository.OrganizationRepository;
import br.com.tekcondo.report.ReportTable.Column;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.supplier.service.ContractService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relatórios gerenciais e financeiros, calculados sempre a partir dos dados gravados. */
@Service
public class ReportService {

    public enum Kind {
        INCOME_EXPENSE, BALANCE_SHEET, CASH_FLOW, DELINQUENCY, PAYABLES, RECEIVABLES, PAYMENTS, ALLOCATIONS, BUDGET,
        BANK_MOVEMENT, EXPENSES_BY_SUPPLIER, OCCURRENCES, CONTRACTS
    }

    public record Params(LocalDate from, LocalDate to, Integer year, UUID bankAccountId) {
    }

    public record Exported(String fileName, String contentType, byte[] content) {
    }

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> STATUS = Map.of("OPEN", "Em aberto", "PARTIAL", "Parcial", "PAID", "Pago",
            "CANCELLED", "Cancelado", "RENEGOTIATED", "Renegociado", "GENERATED", "Gerado");

    private final NamedParameterJdbcTemplate jdbc;
    private final CondominiumAccess access;
    private final CollectionService collection;
    private final BudgetService budget;
    private final TreasuryService treasury;
    private final ContractService contracts;
    private final FinanceQueries queries;
    private final BankAccountRepository bankAccounts;
    private final OrganizationRepository organizations;
    private final ReportExporter exporter;
    private final AuditService auditService;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, CondominiumAccess access, CollectionService collection,
                         BudgetService budget, TreasuryService treasury, ContractService contracts, FinanceQueries queries,
                         BankAccountRepository bankAccounts, OrganizationRepository organizations, ReportExporter exporter,
                         AuditService auditService, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.collection = collection;
        this.budget = budget;
        this.treasury = treasury;
        this.contracts = contracts;
        this.queries = queries;
        this.bankAccounts = bankAccounts;
        this.organizations = organizations;
        this.exporter = exporter;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportTable build(UUID condominiumId, Kind kind, Params p) {
        Condominium c = access.require(condominiumId, Permission.REPORT_VIEW);
        LocalDate to = p.to() == null ? LocalDate.now(clock) : p.to();
        LocalDate from = p.from() == null ? to.withDayOfMonth(1) : p.from();
        if (from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_PERIOD", "A data inicial deve ser anterior à final.");
        }
        String period = c.getName() + " · " + BR.format(from) + " a " + BR.format(to);
        var params = new MapSqlParameterSource("c", condominiumId).addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        return switch (kind) {
            case INCOME_EXPENSE -> incomeExpense(period, params);
            case BALANCE_SHEET -> balanceSheet(condominiumId, period, from, to);
            case CASH_FLOW -> cashFlow(condominiumId, period, from, to, params);
            case DELINQUENCY -> delinquency(condominiumId, c.getName(), to);
            case PAYABLES -> entries(period, params, "EXPENSE");
            case RECEIVABLES -> entries(period, params, "INCOME");
            case PAYMENTS -> payments(period, params);
            case ALLOCATIONS -> allocations(period, params);
            case BUDGET -> budget(condominiumId, c.getName(), p.year() == null ? to.getYear() : p.year());
            case BANK_MOVEMENT -> bankMovement(condominiumId, period, p.bankAccountId(), from, to);
            case EXPENSES_BY_SUPPLIER -> bySupplier(period, params);
            case OCCURRENCES -> occurrences(period, params);
            case CONTRACTS -> contracts(condominiumId, c.getName());
        };
    }

    @Transactional
    public Exported export(UUID condominiumId, Kind kind, Params p, ReportExporter.Format format) {
        Condominium c = access.require(condominiumId, Permission.REPORT_EXPORT);
        ReportTable table = build(condominiumId, kind, p);
        String orgName = organizations.findById(c.getOrganizationId()).map(o -> o.getName()).orElse(null);
        auditService.record(AuditAction.REPORT_EXPORTED, "Report", condominiumId, Map.of("report", kind.name(),
                "format", format.name(), "rows", table.rows().size()));
        String file = kind.name().toLowerCase().replace('_', '-') + "-" + LocalDate.now(clock) + "." + format.extension;
        return new Exported(file, format.contentType, exporter.export(table, format, orgName));
    }

    // ------------------------------------------------------------------ relatórios

    private ReportTable incomeExpense(String period, MapSqlParameterSource params) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select e.kind, a.code, a.name, sum(p.total) total
                  from entry_payment p join financial_entry e on e.id = p.entry_id join chart_account a on a.id = e.chart_account_id
                 where e.condominium_id = :c and p.reversed_at is null and p.paid_on between :from and :to
                 group by e.kind, a.code, a.name order by e.kind desc, a.code
                """, params, rs -> {
            boolean income = "INCOME".equals(rs.getString("kind"));
            BigDecimal total = rs.getBigDecimal("total");
            totals[income ? 0 : 1] = totals[income ? 0 : 1].add(total);
            rows.add(row(income ? "Receita" : "Despesa", rs.getString("code"), rs.getString("name"), total));
        });
        return new ReportTable("Receitas e despesas (regime de caixa)", period,
                List.of(Column.text("type", "Tipo"), Column.text("code", "Código"), Column.text("account", "Conta"), Column.money("total", "Total")),
                rows, row("", "", "Resultado do período", totals[0].subtract(totals[1])),
                List.of("Receitas: " + money(totals[0]) + " · Despesas: " + money(totals[1]),
                        "Valores pagos/recebidos no período (inclui multa e juros, descontados os abatimentos; baixas estornadas não entram)."));
    }

    private ReportTable balanceSheet(UUID condominiumId, String period, LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (BankAccount b : bankAccounts.findByCondominiumIdOrderByNameAsc(condominiumId)) {
            BigDecimal opening = queries.balanceBefore(b.getId(), b.getOpeningBalance(), from);
            List<FinanceDtos.StatementLine> lines = queries.movements(b.getId(), opening, from, to);
            BigDecimal in = lines.stream().map(FinanceDtos.StatementLine::amount).filter(a -> a.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal out = lines.stream().map(FinanceDtos.StatementLine::amount).filter(a -> a.signum() < 0).reduce(BigDecimal.ZERO, BigDecimal::add).negate();
            BigDecimal closing = opening.add(in).subtract(out);
            rows.add(row(b.getName(), opening, in, out, closing));
            t[0] = t[0].add(opening);
            t[1] = t[1].add(in);
            t[2] = t[2].add(out);
            t[3] = t[3].add(closing);
        }
        return new ReportTable("Balancete financeiro", period,
                List.of(Column.text("account", "Conta"), Column.money("opening", "Saldo inicial"), Column.money("in", "Entradas"),
                        Column.money("out", "Saídas"), Column.money("closing", "Saldo final")),
                rows, row("Total", t[0], t[1], t[2], t[3]),
                List.of("Entradas e saídas incluem transferências entre contas do próprio condomínio."));
    }

    private ReportTable cashFlow(UUID condominiumId, String period, LocalDate from, LocalDate to, MapSqlParameterSource params) {
        Map<YearMonth, BigDecimal[]> months = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            months.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        jdbc.query("""
                select date_trunc('month', p.paid_on)::date m, e.kind, sum(p.total) total
                  from entry_payment p join financial_entry e on e.id = p.entry_id
                 where e.condominium_id = :c and p.reversed_at is null and p.paid_on between :from and :to
                 group by 1, 2
                """, params, rs -> {
            BigDecimal[] v = months.get(YearMonth.from(rs.getDate("m").toLocalDate()));
            if (v != null) v["INCOME".equals(rs.getString("kind")) ? 0 : 1] = rs.getBigDecimal("total");
        });
        BigDecimal balance = bankAccounts.findByCondominiumIdOrderByNameAsc(condominiumId).stream()
                .map(b -> queries.balanceBefore(b.getId(), b.getOpeningBalance(), from)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal startBalance = balance;
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal tin = BigDecimal.ZERO;
        BigDecimal tout = BigDecimal.ZERO;
        for (var e : months.entrySet()) {
            BigDecimal net = e.getValue()[0].subtract(e.getValue()[1]);
            balance = balance.add(net);
            tin = tin.add(e.getValue()[0]);
            tout = tout.add(e.getValue()[1]);
            rows.add(row(e.getKey().format(DateTimeFormatter.ofPattern("MM/yyyy")), e.getValue()[0], e.getValue()[1], net, balance));
        }
        return new ReportTable("Fluxo de caixa mensal", period,
                List.of(Column.text("month", "Mês"), Column.money("in", "Entradas"), Column.money("out", "Saídas"),
                        Column.money("net", "Resultado"), Column.money("balance", "Saldo acumulado")),
                rows, row("Total", tin, tout, tin.subtract(tout), balance),
                List.of("Saldo inicial consolidado das contas: " + money(startBalance) + "."));
    }

    private ReportTable delinquency(UUID condominiumId, String condoName, LocalDate asOf) {
        BillingDtos.DelinquencyReport r = collection.delinquency(condominiumId, asOf);
        List<List<Object>> rows = r.rows().stream().map(x -> row(x.unitLabel(), x.responsibleName(), x.overdueCount(),
                x.oldestDueDate(), x.maxDaysLate(), x.principal(), x.charges(), x.total())).toList();
        return new ReportTable("Inadimplência", condoName + " · posição em " + BR.format(asOf),
                List.of(Column.text("unit", "Unidade"), Column.text("responsible", "Responsável"), Column.number("count", "Títulos"),
                        Column.date("oldest", "Venc. mais antigo"), Column.number("days", "Dias de atraso"),
                        Column.money("principal", "Principal"), Column.money("charges", "Multa + juros"), Column.money("total", "Total atualizado")),
                rows, row("Total", "", "", null, "", r.totalPrincipal(), r.totalCharges(), r.total()),
                List.of("Índice de inadimplência (principal vencido ÷ emitido nos últimos 12 meses): "
                        + r.delinquencyRate().toPlainString().replace('.', ',') + "% · " + r.unitsInDebt() + " de " + r.activeUnits() + " unidades."));
    }

    private ReportTable entries(String period, MapSqlParameterSource params, String kind) {
        List<List<Object>> rows = new ArrayList<>();
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
                """, params.addValue("kind", kind), rs -> {
            BigDecimal amount = rs.getBigDecimal("amount");
            BigDecimal paid = rs.getBigDecimal("paid_amount");
            t[0] = t[0].add(amount);
            t[1] = t[1].add(paid);
            t[2] = t[2].add(amount.subtract(paid));
            rows.add(row(rs.getDate("due_date").toLocalDate(), rs.getString("description"), rs.getString("party"),
                    rs.getString("account"), amount, paid, amount.subtract(paid), STATUS.getOrDefault(rs.getString("status"), rs.getString("status"))));
        });
        boolean income = "INCOME".equals(kind);
        return new ReportTable(income ? "Contas a receber" : "Contas a pagar", period + " (por vencimento)",
                List.of(Column.date("due", "Vencimento"), Column.text("description", "Descrição"),
                        Column.text("party", income ? "Unidade" : "Fornecedor"), Column.text("account", "Conta"),
                        Column.money("amount", "Valor"), Column.money("paid", income ? "Recebido" : "Pago"),
                        Column.money("open", "Em aberto"), Column.text("status", "Situação")),
                rows, row(null, "Total", "", "", t[0], t[1], t[2], ""), null);
    }

    private ReportTable payments(String period, MapSqlParameterSource params) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select p.paid_on, e.kind, e.description, ba.name bank, p.principal, p.fine + p.interest charges, p.discount, p.total
                  from entry_payment p join financial_entry e on e.id = p.entry_id join bank_account ba on ba.id = p.bank_account_id
                 where e.condominium_id = :c and p.reversed_at is null and p.paid_on between :from and :to
                 order by p.paid_on, e.description
                """, params, rs -> {
            boolean income = "INCOME".equals(rs.getString("kind"));
            BigDecimal total = rs.getBigDecimal("total");
            t[income ? 0 : 1] = t[income ? 0 : 1].add(total);
            rows.add(row(rs.getDate("paid_on").toLocalDate(), income ? "Recebimento" : "Pagamento", rs.getString("description"),
                    rs.getString("bank"), rs.getBigDecimal("principal"), rs.getBigDecimal("charges"), rs.getBigDecimal("discount"), total));
        });
        return new ReportTable("Pagamentos e recebimentos realizados", period,
                List.of(Column.date("date", "Data"), Column.text("type", "Tipo"), Column.text("description", "Descrição"),
                        Column.text("bank", "Conta"), Column.money("principal", "Principal"), Column.money("charges", "Multa/juros"),
                        Column.money("discount", "Desconto"), Column.money("total", "Total")),
                rows, null, List.of("Total recebido: " + money(t[0]) + " · Total pago: " + money(t[1])));
    }

    private ReportTable allocations(String period, MapSqlParameterSource params) {
        List<List<Object>> rows = new ArrayList<>();
        jdbc.query("""
                select a.competence, a.description, a.method, a.units_count, a.generated_amount, a.status,
                       coalesce((select sum(e.paid_amount) from financial_entry e where e.allocation_id = a.id), 0) received
                  from allocation a where a.condominium_id = :c and a.competence between date_trunc('month', cast(:from as date)) and :to
                 order by a.competence, a.description
                """, params, rs -> {
            rows.add(row(YearMonth.from(rs.getDate("competence").toLocalDate()).format(DateTimeFormatter.ofPattern("MM/yyyy")),
                    rs.getString("description"), methodLabel(rs.getString("method")), rs.getInt("units_count"),
                    rs.getBigDecimal("generated_amount"), rs.getBigDecimal("received"),
                    STATUS.getOrDefault(rs.getString("status"), rs.getString("status"))));
        });
        return new ReportTable("Rateios e cobranças geradas", period,
                List.of(Column.text("competence", "Competência"), Column.text("description", "Descrição"), Column.text("method", "Critério"),
                        Column.number("units", "Unidades"), Column.money("generated", "Gerado"), Column.money("received", "Recebido"),
                        Column.text("status", "Situação")), rows, null, null);
    }

    private ReportTable budget(UUID condominiumId, String condoName, int year) {
        FinanceDtos.Budget b = budget.budget(condominiumId, year);
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal tb = BigDecimal.ZERO;
        BigDecimal tr = BigDecimal.ZERO;
        for (FinanceDtos.BudgetRow r : b.rows()) {
            if (r.totalBudgeted().signum() == 0 && r.totalRealized().signum() == 0) continue;
            BigDecimal pct = r.totalBudgeted().signum() == 0 ? null
                    : r.totalRealized().multiply(new BigDecimal("100")).divide(r.totalBudgeted(), 2, java.math.RoundingMode.HALF_UP);
            rows.add(row(r.code() + " " + r.name(), r.nature() == br.com.tekcondo.finance.entity.ChartAccount.Nature.INCOME ? "Receita" : "Despesa",
                    r.totalBudgeted(), r.totalRealized(), r.totalRealized().subtract(r.totalBudgeted()), pct));
        }
        return new ReportTable("Orçamento previsto × realizado " + year, condoName,
                List.of(Column.text("account", "Conta"), Column.text("nature", "Natureza"), Column.money("budgeted", "Previsto"),
                        Column.money("realized", "Realizado"), Column.money("diff", "Diferença"), Column.percent("pct", "Execução")),
                rows, null, List.of("Realizado em regime de caixa (pagamentos efetivados no ano)."));
    }

    private ReportTable bankMovement(UUID condominiumId, String period, UUID bankAccountId, LocalDate from, LocalDate to) {
        if (bankAccountId == null) {
            throw ApiException.badRequest("BANK_ACCOUNT_REQUIRED", "Selecione a conta bancária.");
        }
        FinanceDtos.Statement s = treasury.statement(condominiumId, bankAccountId, from, to);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row(from, "Saldo anterior", null, s.openingBalance(), ""));
        for (FinanceDtos.StatementLine l : s.lines()) {
            rows.add(row(l.date(), l.description(), l.amount(), l.balance(), l.reconciled() ? "Sim" : "Não"));
        }
        return new ReportTable("Movimentação da conta " + s.bankAccountName(), period,
                List.of(Column.date("date", "Data"), Column.text("description", "Histórico"), Column.money("amount", "Valor"),
                        Column.money("balance", "Saldo"), Column.text("reconciled", "Conciliado")),
                rows, row(to, "Saldo final", null, s.closingBalance(), ""),
                List.of("Saldo conciliado em " + BR.format(to) + ": " + money(s.reconciledBalance())));
    }

    private ReportTable bySupplier(String period, MapSqlParameterSource params) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal[] total = {BigDecimal.ZERO};
        jdbc.query("""
                select coalesce(s.name, 'Sem fornecedor') supplier, s.document, count(distinct e.id) n, sum(p.total) total
                  from entry_payment p join financial_entry e on e.id = p.entry_id left join supplier s on s.id = e.supplier_id
                 where e.condominium_id = :c and e.kind = 'EXPENSE' and p.reversed_at is null and p.paid_on between :from and :to
                 group by s.name, s.document order by total desc
                """, params, rs -> {
            total[0] = total[0].add(rs.getBigDecimal("total"));
            rows.add(row(rs.getString("supplier"), rs.getString("document"), rs.getInt("n"), rs.getBigDecimal("total")));
        });
        return new ReportTable("Despesas por fornecedor", period,
                List.of(Column.text("supplier", "Fornecedor"), Column.text("document", "CPF/CNPJ"), Column.number("count", "Lançamentos"),
                        Column.money("total", "Total pago")), rows, row("Total", "", "", total[0]), null);
    }

    private ReportTable occurrences(String period, MapSqlParameterSource params) {
        List<List<Object>> rows = new ArrayList<>();
        jdbc.query("""
                select number, created_at, title, category, priority, status, resolved_at, assignee_name
                  from occurrence where condominium_id = :c and created_at::date between :from and :to order by number
                """, params, rs -> {
            rows.add(row(rs.getInt("number"), rs.getTimestamp("created_at").toLocalDateTime().toLocalDate(),
                    rs.getString("title"), rs.getString("category"), rs.getString("priority"), rs.getString("status"),
                    rs.getString("assignee_name"), rs.getTimestamp("resolved_at") == null ? null
                            : rs.getTimestamp("resolved_at").toLocalDateTime().toLocalDate()));
        });
        return new ReportTable("Histórico de ocorrências", period,
                List.of(Column.number("number", "Nº"), Column.date("created", "Abertura"), Column.text("title", "Título"),
                        Column.text("category", "Categoria"), Column.text("priority", "Prioridade"), Column.text("status", "Situação"),
                        Column.text("assignee", "Responsável"), Column.date("resolved", "Encerramento")), rows, null, null);
    }

    private ReportTable contracts(UUID condominiumId, String condoName) {
        List<List<Object>> rows = contracts.list(condominiumId).stream().map(c -> row(c.supplierName(), c.title(), c.amount(),
                c.periodicity().name(), c.startDate(), c.endDate(), c.status() == br.com.tekcondo.supplier.entity.Contract.Status.TERMINATED
                        ? "Encerrado" : c.expired() ? "Vencido" : c.expiring() ? "A vencer" : "Vigente")).toList();
        return new ReportTable("Contratos", condoName,
                List.of(Column.text("supplier", "Fornecedor"), Column.text("title", "Objeto"), Column.money("amount", "Valor"),
                        Column.text("periodicity", "Periodicidade"), Column.date("start", "Início"), Column.date("end", "Término"),
                        Column.text("status", "Situação")), rows, null,
                List.of("\"A vencer\" = término dentro do prazo de aviso prévio do contrato."));
    }

    // ------------------------------------------------------------------

    private static List<Object> row(Object... values) {
        return java.util.Arrays.asList(values);
    }

    private static String money(BigDecimal v) {
        return ReportExporter.format(v, Column.money("x", "x"));
    }

    private static String methodLabel(String method) {
        return switch (method) {
            case "IDEAL_FRACTION" -> "Fração ideal";
            case "EQUAL" -> "Igualitário";
            case "FIXED" -> "Valor fixo";
            case "CONSUMPTION" -> "Consumo";
            default -> method;
        };
    }
}
