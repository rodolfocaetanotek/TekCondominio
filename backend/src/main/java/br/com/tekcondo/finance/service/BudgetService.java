package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.dto.FinanceDtos.Budget;
import br.com.tekcondo.finance.dto.FinanceDtos.BudgetCell;
import br.com.tekcondo.finance.dto.FinanceDtos.BudgetRow;
import br.com.tekcondo.finance.entity.BudgetLine;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.repository.BudgetLineRepository;
import br.com.tekcondo.finance.repository.ChartAccountRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orçamento anual por conta e mês, comparado com o realizado (regime de caixa). */
@Service
public class BudgetService {

    private final BudgetLineRepository lines;
    private final ChartAccountRepository chartAccounts;
    private final FinanceSetupService setup;
    private final FinanceQueries queries;
    private final CondominiumAccess access;
    private final AuditoriaService auditService;

    public BudgetService(BudgetLineRepository lines, ChartAccountRepository chartAccounts, FinanceSetupService setup,
                         FinanceQueries queries, CondominiumAccess access, AuditoriaService auditService) {
        this.lines = lines;
        this.chartAccounts = chartAccounts;
        this.setup = setup;
        this.queries = queries;
        this.access = access;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Budget budget(UUID condominiumId, int year) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        return build(condominiumId, year);
    }

    @Transactional
    public Budget save(UUID condominiumId, int year, List<BudgetCell> cells) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        Map<String, BudgetLine> existing = new HashMap<>();
        lines.findByCondominiumIdAndYear(condominiumId, year)
                .forEach(l -> existing.put(l.getChartAccountId() + ":" + l.getMonth(), l));
        UUID by = access.user().idUsuario();
        for (BudgetCell cell : cells) {
            setup.analyticAccount(cell.chartAccountId(), null);
            BudgetLine line = existing.computeIfAbsent(cell.chartAccountId() + ":" + cell.month(),
                    k -> new BudgetLine(condominium.getOrganizationId(), condominiumId, year, cell.month(), cell.chartAccountId()));
            line.setAmount(cell.amount(), by);
            lines.save(line);
        }
        auditService.registrar(AcaoAuditoria.BUDGET_UPDATED, "Condominium", condominiumId, Map.of("year", year, "cells", cells.size()));
        return build(condominiumId, year);
    }

    private Budget build(UUID condominiumId, int year) {
        Map<UUID, BigDecimal[]> budgeted = new HashMap<>();
        for (BudgetLine l : lines.findByCondominiumIdAndYear(condominiumId, year)) {
            budgeted.computeIfAbsent(l.getChartAccountId(), k -> zeros())[l.getMonth() - 1] = l.getAmount();
        }
        Map<UUID, BigDecimal[]> realized = queries.realizedByAccountAndMonth(condominiumId, year);
        List<BudgetRow> rows = chartAccounts.findAvailable(access.organizationId()).stream()
                .filter(ChartAccount::isAnalytic)
                .filter(a -> a.isActive() || budgeted.containsKey(a.getId()) || realized.containsKey(a.getId()))
                .map(a -> {
                    BigDecimal[] b = budgeted.getOrDefault(a.getId(), zeros());
                    BigDecimal[] r = realized.getOrDefault(a.getId(), zeros());
                    return new BudgetRow(a.getId(), a.getCode(), a.getName(), a.getNature(), Arrays.asList(b),
                            Arrays.asList(r), sum(b), sum(r));
                })
                .toList();
        return new Budget(year, rows);
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] m = new BigDecimal[12];
        Arrays.fill(m, BigDecimal.ZERO);
        return m;
    }

    private static BigDecimal sum(BigDecimal[] values) {
        return Arrays.stream(values).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
