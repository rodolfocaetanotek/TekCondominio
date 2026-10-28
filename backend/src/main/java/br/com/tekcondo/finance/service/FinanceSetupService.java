package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.dto.FinanceDtos.BankAccountRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.BankAccountResponse;
import br.com.tekcondo.finance.dto.FinanceDtos.ChartAccountRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.ChartAccountResponse;
import br.com.tekcondo.finance.dto.FinanceDtos.CostCenterResponse;
import br.com.tekcondo.finance.dto.FinanceDtos.Settings;
import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.CostCenter;
import br.com.tekcondo.finance.repository.BankAccountRepository;
import br.com.tekcondo.finance.repository.ChartAccountRepository;
import br.com.tekcondo.finance.repository.CostCenterRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceSetupService {

    private final CondominiumAccess access;
    private final AcessoService accessService;
    private final ChartAccountRepository chartAccounts;
    private final CostCenterRepository costCenters;
    private final BankAccountRepository bankAccounts;
    private final FinanceQueries queries;
    private final AuditoriaService auditService;
    private final Clock clock;

    public FinanceSetupService(CondominiumAccess access, AcessoService accessService, ChartAccountRepository chartAccounts,
                               CostCenterRepository costCenters, BankAccountRepository bankAccounts,
                               FinanceQueries queries, AuditoriaService auditService, Clock clock) {
        this.access = access;
        this.accessService = accessService;
        this.chartAccounts = chartAccounts;
        this.costCenters = costCenters;
        this.bankAccounts = bankAccounts;
        this.queries = queries;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- parâmetros

    @Transactional(readOnly = true)
    public Settings settings(UUID condominiumId) {
        Condominium c = access.require(condominiumId, Permissao.FINANCE_VIEW);
        return new Settings(c.getFinePercent(), c.getInterestMonthlyPercent(), c.getBillingDueDay(), c.isRequirePayableApproval());
    }

    @Transactional
    public Settings updateSettings(UUID condominiumId, Settings request) {
        Condominium c = access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        accessService.exigir(Permissao.CONDOMINIUM_EDIT, condominiumId);
        Settings before = new Settings(c.getFinePercent(), c.getInterestMonthlyPercent(), c.getBillingDueDay(), c.isRequirePayableApproval());
        c.updateFinancialSettings(request.finePercent(), request.interestMonthlyPercent(), request.billingDueDay(),
                request.requirePayableApproval(), access.user().idUsuario());
        auditService.registrar(AcaoAuditoria.FINANCE_SETTINGS_CHANGED, "Condominium", condominiumId,
                Map.of("from", before.toString(), "to", request.toString()));
        return request;
    }

    // ---------------------------------------------------------------- plano de contas

    @Transactional(readOnly = true)
    public List<ChartAccountResponse> chartAccounts() {
        accessService.exigirEscopo(Permissao.FINANCE_VIEW);
        return chartAccounts.findAvailable(access.organizationId()).stream().map(ChartAccountResponse::from).toList();
    }

    @Transactional
    public ChartAccountResponse createChartAccount(ChartAccountRequest request) {
        accessService.exigirNaAdministradora(Permissao.FINANCE_EDIT);
        UUID orgId = access.organizationId();
        String code = request.code().trim();
        if (chartAccounts.existsCode(orgId, code)) {
            throw ApiException.conflito("CHART_ACCOUNT_CODE_TAKEN", "Já existe uma conta com este código.");
        }
        ChartAccount account = chartAccounts.save(new ChartAccount(orgId, code, request.name(), request.nature()));
        auditService.registrar(AcaoAuditoria.CHART_ACCOUNT_CREATED, "ChartAccount", account.getId(), Map.of("code", code, "name", account.getName()));
        return ChartAccountResponse.from(account);
    }

    @Transactional
    public ChartAccountResponse updateChartAccount(UUID id, String name, boolean active) {
        accessService.exigirNaAdministradora(Permissao.FINANCE_EDIT);
        ChartAccount account = chartAccounts.findAvailable(id, access.organizationId())
                .orElseThrow(() -> ApiException.naoEncontrado("Conta"));
        if (account.isSystem()) {
            throw ApiException.regraNegocio("SYSTEM_ACCOUNT", "Contas do plano padrão não podem ser alteradas. Crie uma conta própria.");
        }
        account.rename(name);
        account.setActive(active);
        auditService.registrar(AcaoAuditoria.CHART_ACCOUNT_UPDATED, "ChartAccount", id, Map.of("name", name, "active", active));
        return ChartAccountResponse.from(account);
    }

    public ChartAccount analyticAccount(UUID id, ChartAccount.Nature expected) {
        ChartAccount account = chartAccounts.findAvailable(id, access.organizationId())
                .orElseThrow(() -> ApiException.naoEncontrado("Conta do plano de contas"));
        if (!account.isAnalytic() || !account.isActive()) {
            throw ApiException.regraNegocio("ACCOUNT_NOT_POSTABLE", "Escolha uma conta analítica ativa (não um grupo).");
        }
        if (expected != null && account.getNature() != expected) {
            throw ApiException.regraNegocio("ACCOUNT_NATURE_MISMATCH", expected == ChartAccount.Nature.INCOME
                    ? "Receitas devem usar uma conta de receita." : "Despesas devem usar uma conta de despesa.");
        }
        return account;
    }

    // ---------------------------------------------------------------- centros de custo

    @Transactional(readOnly = true)
    public List<CostCenterResponse> costCenters(UUID condominiumId) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        return costCenters.findByCondominiumIdOrderByNameAsc(condominiumId).stream()
                .map(c -> new CostCenterResponse(c.getId(), c.getName(), c.isActive())).toList();
    }

    @Transactional
    public CostCenterResponse saveCostCenter(UUID condominiumId, UUID id, String name, Boolean active) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        costCenters.findByName(condominiumId, name.trim()).filter(c -> !c.getId().equals(id)).ifPresent(c -> {
            throw ApiException.conflito("COST_CENTER_NAME_TAKEN", "Já existe um centro de custo com este nome.");
        });
        CostCenter center = id == null ? new CostCenter(condominium.getOrganizationId(), condominiumId, name)
                : costCenters.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Centro de custo"));
        center.rename(name);
        if (active != null) {
            center.setActive(active);
        }
        costCenters.save(center);
        auditService.registrar(AcaoAuditoria.COST_CENTER_SAVED, "CostCenter", center.getId(), Map.of("name", center.getName()));
        return new CostCenterResponse(center.getId(), center.getName(), center.isActive());
    }

    // ---------------------------------------------------------------- contas bancárias

    @Transactional(readOnly = true)
    public List<BankAccountResponse> bankAccounts(UUID condominiumId) {
        access.require(condominiumId, Permissao.FINANCE_VIEW);
        Map<UUID, BigDecimal> balances = queries.balances(condominiumId, LocalDate.now(clock));
        return bankAccounts.findByCondominiumIdOrderByNameAsc(condominiumId).stream()
                .map(b -> toResponse(b, balances.getOrDefault(b.getId(), b.getOpeningBalance()))).toList();
    }

    @Transactional
    public BankAccountResponse createBankAccount(UUID condominiumId, BankAccountRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        ensureBankNameFree(condominiumId, request.name(), null);
        BankAccount account = new BankAccount(condominium.getOrganizationId(), condominiumId, request.kind(),
                request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance(),
                request.openingDate() == null ? LocalDate.now(clock) : request.openingDate());
        account.update(request.name(), blank(request.bankCode()), blank(request.agency()), blank(request.accountNumber()));
        bankAccounts.save(account);
        auditService.registrar(AcaoAuditoria.BANK_ACCOUNT_SAVED, "BankAccount", account.getId(), Map.of("name", account.getName(),
                "openingBalance", account.getOpeningBalance().toPlainString()));
        return toResponse(account, account.getOpeningBalance());
    }

    @Transactional
    public BankAccountResponse updateBankAccount(UUID condominiumId, UUID id, BankAccountRequest request, Boolean active) {
        access.requireActive(condominiumId, Permissao.FINANCE_EDIT);
        BankAccount account = loadBankAccount(condominiumId, id);
        ensureBankNameFree(condominiumId, request.name(), id);
        account.update(request.name(), blank(request.bankCode()), blank(request.agency()), blank(request.accountNumber()));
        if (active != null) {
            account.setActive(active);
        }
        auditService.registrar(AcaoAuditoria.BANK_ACCOUNT_SAVED, "BankAccount", id, Map.of("name", account.getName(), "active", account.isActive()));
        return toResponse(account, queries.balances(condominiumId, LocalDate.now(clock)).getOrDefault(id, account.getOpeningBalance()));
    }

    public BankAccount loadBankAccount(UUID condominiumId, UUID id) {
        return bankAccounts.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Conta bancária"));
    }

    public CostCenter loadCostCenter(UUID condominiumId, UUID id) {
        CostCenter center = costCenters.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Centro de custo"));
        if (!center.isActive()) {
            throw ApiException.regraNegocio("COST_CENTER_INACTIVE", "O centro de custo está inativo.");
        }
        return center;
    }

    private void ensureBankNameFree(UUID condominiumId, String name, UUID exceptId) {
        bankAccounts.findByName(condominiumId, name.trim().toLowerCase(Locale.ROOT)).filter(b -> !b.getId().equals(exceptId))
                .ifPresent(b -> {
                    throw ApiException.conflito("BANK_ACCOUNT_NAME_TAKEN", "Já existe uma conta com este nome.");
                });
    }

    private static BankAccountResponse toResponse(BankAccount b, BigDecimal balance) {
        return new BankAccountResponse(b.getId(), b.getName(), b.getKind(), b.getBankCode(), b.getAgency(),
                b.getAccountNumber(), b.getOpeningBalance(), b.getOpeningDate(), b.isActive(), balance);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
