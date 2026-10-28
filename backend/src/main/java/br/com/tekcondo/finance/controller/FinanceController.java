package br.com.tekcondo.finance.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.finance.dto.FinanceDtos.BankAccountRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.BankAccountResponse;
import br.com.tekcondo.finance.dto.FinanceDtos.Budget;
import br.com.tekcondo.finance.dto.FinanceDtos.BudgetRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.ChartAccountRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.ChartAccountResponse;
import br.com.tekcondo.finance.dto.FinanceDtos.CostCenterResponse;
import br.com.tekcondo.finance.dto.FinanceDtos.DecisionRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.EntryDetail;
import br.com.tekcondo.finance.dto.FinanceDtos.EntryRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.EntrySummary;
import br.com.tekcondo.finance.dto.FinanceDtos.NameRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.PaymentQuote;
import br.com.tekcondo.finance.dto.FinanceDtos.PaymentRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.ReasonRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.ReconcileRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.Settings;
import br.com.tekcondo.finance.dto.FinanceDtos.Statement;
import br.com.tekcondo.finance.dto.FinanceDtos.TransferRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.TransferResponse;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.service.BudgetService;
import br.com.tekcondo.finance.service.EntryService;
import br.com.tekcondo.finance.service.FinanceSetupService;
import br.com.tekcondo.finance.service.TreasuryService;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Financeiro")
public class FinanceController {

    private static final Map<String, String> ENTRY_SORTS = Map.of("dueDate", "dueDate", "amount", "amount",
            "description", "description", "createdAt", "createdAt", "competence", "competence");

    private final FinanceSetupService setup;
    private final EntryService entries;
    private final TreasuryService treasury;
    private final BudgetService budget;

    public FinanceController(FinanceSetupService setup, EntryService entries, TreasuryService treasury, BudgetService budget) {
        this.setup = setup;
        this.entries = entries;
        this.treasury = treasury;
        this.budget = budget;
    }

    // ---------- plano de contas (da administradora)
    @GetMapping("/finance/chart-accounts")
    @Operation(summary = "Plano de contas (padrão + contas próprias)")
    public List<ChartAccountResponse> chartAccounts() {
        return setup.chartAccounts();
    }

    @PostMapping("/finance/chart-accounts")
    public ResponseEntity<ChartAccountResponse> createChartAccount(@Valid @RequestBody ChartAccountRequest request) {
        return ResponseEntity.status(201).body(setup.createChartAccount(request));
    }

    @PutMapping("/finance/chart-accounts/{id}")
    public ChartAccountResponse updateChartAccount(@PathVariable UUID id, @Valid @RequestBody NameRequest request,
                                                   @RequestParam(defaultValue = "true") boolean active) {
        return setup.updateChartAccount(id, request.name(), active);
    }

    // ---------- parâmetros, centros de custo e contas
    @GetMapping("/condominios/{c}/finance/settings")
    @Operation(summary = "Parâmetros financeiros (multa, juros, vencimento, aprovação de pagamentos)")
    public Settings settings(@PathVariable UUID c) {
        return setup.settings(c);
    }

    @PutMapping("/condominios/{c}/finance/settings")
    public Settings updateSettings(@PathVariable UUID c, @Valid @RequestBody Settings request) {
        return setup.updateSettings(c, request);
    }

    @GetMapping("/condominios/{c}/finance/cost-centers")
    public List<CostCenterResponse> costCenters(@PathVariable UUID c) {
        return setup.costCenters(c);
    }

    @PostMapping("/condominios/{c}/finance/cost-centers")
    public ResponseEntity<CostCenterResponse> createCostCenter(@PathVariable UUID c, @Valid @RequestBody NameRequest request) {
        return ResponseEntity.status(201).body(setup.saveCostCenter(c, null, request.name(), null));
    }

    @PutMapping("/condominios/{c}/finance/cost-centers/{id}")
    public CostCenterResponse updateCostCenter(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody NameRequest request,
                                               @RequestParam(required = false) Boolean active) {
        return setup.saveCostCenter(c, id, request.name(), active);
    }

    @GetMapping("/condominios/{c}/finance/bank-accounts")
    @Operation(summary = "Contas bancárias e caixa, com saldo atual calculado")
    public List<BankAccountResponse> bankAccounts(@PathVariable UUID c) {
        return setup.bankAccounts(c);
    }

    @PostMapping("/condominios/{c}/finance/bank-accounts")
    public ResponseEntity<BankAccountResponse> createBankAccount(@PathVariable UUID c, @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.status(201).body(setup.createBankAccount(c, request));
    }

    @PutMapping("/condominios/{c}/finance/bank-accounts/{id}")
    public BankAccountResponse updateBankAccount(@PathVariable UUID c, @PathVariable UUID id,
                                                 @Valid @RequestBody BankAccountRequest request,
                                                 @RequestParam(required = false) Boolean active) {
        return setup.updateBankAccount(c, id, request, active);
    }

    @GetMapping("/condominios/{c}/finance/bank-accounts/{id}/statement")
    @Operation(summary = "Extrato da conta com saldo acumulado e situação de conciliação")
    public Statement statement(@PathVariable UUID c, @PathVariable UUID id,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return treasury.statement(c, id, from, to);
    }

    @PostMapping("/condominios/{c}/finance/reconciliation")
    @Operation(summary = "Concilia (ou desfaz) movimentações com o extrato bancário")
    public Map<String, Integer> reconcile(@PathVariable UUID c, @Valid @RequestBody ReconcileRequest request) {
        return Map.of("updated", treasury.reconcile(c, request.items(), request.reconciled()));
    }

    // ---------- lançamentos
    @GetMapping("/condominios/{c}/finance/entries")
    @Operation(summary = "Contas a pagar e a receber", description = "Filtros: kind, status (lista), dueFrom, dueTo, chartAccountId, supplierId, unitId, search, overdue, approval")
    public PaginaResponse<EntrySummary> entries(@PathVariable UUID c,
                                              @RequestParam(required = false) FinancialEntry.Kind kind,
                                              @RequestParam(required = false) List<FinancialEntry.Status> status,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
                                              @RequestParam(required = false) UUID chartAccountId,
                                              @RequestParam(required = false) UUID supplierId,
                                              @RequestParam(required = false) UUID unitId,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(required = false) Boolean overdue,
                                              @RequestParam(required = false) FinancialEntry.Approval approval,
                                              @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "50") int tamanho,
                                              @RequestParam(required = false) String ordem) {
        return entries.list(c, new EntryService.Filter(kind, status, dueFrom, dueTo, chartAccountId, supplierId, unitId, search,
                overdue, approval), PaginaRequests.of(pagina, tamanho, ordem, ENTRY_SORTS, Sort.by("dueDate").and(Sort.by("id"))));
    }

    @GetMapping("/condominios/{c}/finance/entries/{id}")
    public EntryDetail entry(@PathVariable UUID c, @PathVariable UUID id) {
        return entries.get(c, id);
    }

    @PostMapping("/condominios/{c}/finance/entries")
    @Operation(summary = "Lança receita ou despesa (repeat > 1 gera parcelas/recorrência mensal)")
    public ResponseEntity<List<EntryDetail>> createEntry(@PathVariable UUID c, @Valid @RequestBody EntryRequest request) {
        return ResponseEntity.status(201).body(entries.create(c, request));
    }

    @PutMapping("/condominios/{c}/finance/entries/{id}")
    public EntryDetail updateEntry(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody EntryRequest request) {
        return entries.update(c, id, request);
    }

    @PostMapping("/condominios/{c}/finance/entries/{id}/approve")
    public EntryDetail approve(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return entries.approve(c, id, true, request == null ? null : request.note());
    }

    @PostMapping("/condominios/{c}/finance/entries/{id}/reject")
    public EntryDetail reject(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return entries.approve(c, id, false, request.note());
    }

    @PostMapping("/condominios/{c}/finance/entries/{id}/cancel")
    @Operation(summary = "Cancela o lançamento (sem baixas ativas), com justificativa")
    public EntryDetail cancel(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return entries.cancel(c, id, request.reason());
    }

    @GetMapping("/condominios/{c}/finance/entries/{id}/quote")
    @Operation(summary = "Calcula multa e juros para pagamento na data informada")
    public PaymentQuote quote(@PathVariable UUID c, @PathVariable UUID id,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return entries.quote(c, id, date);
    }

    @PostMapping("/condominios/{c}/finance/entries/{id}/payments")
    @Operation(summary = "Registra baixa total ou parcial")
    public EntryDetail pay(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return entries.pay(c, id, request);
    }

    @PostMapping("/condominios/{c}/finance/payments/{paymentId}/reverse")
    @Operation(summary = "Estorna uma baixa, com justificativa")
    public EntryDetail reversePayment(@PathVariable UUID c, @PathVariable UUID paymentId, @Valid @RequestBody ReasonRequest request) {
        return entries.reversePayment(c, paymentId, request.reason());
    }

    // ---------- transferências e orçamento
    @GetMapping("/condominios/{c}/finance/transfers")
    public List<TransferResponse> transfers(@PathVariable UUID c) {
        return treasury.transfers(c);
    }

    @PostMapping("/condominios/{c}/finance/transfers")
    public ResponseEntity<TransferResponse> transfer(@PathVariable UUID c, @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(201).body(treasury.transfer(c, request));
    }

    @PostMapping("/condominios/{c}/finance/transfers/{id}/reverse")
    public TransferResponse reverseTransfer(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return treasury.reverseTransfer(c, id, request.reason());
    }

    @GetMapping("/condominios/{c}/finance/budget/{year}")
    @Operation(summary = "Orçamento anual: previsto × realizado por conta e mês")
    public Budget budget(@PathVariable UUID c, @PathVariable int year) {
        return budget.budget(c, year);
    }

    @PutMapping("/condominios/{c}/finance/budget/{year}")
    public Budget saveBudget(@PathVariable UUID c, @PathVariable int year, @Valid @RequestBody BudgetRequest request) {
        return budget.save(c, year, request.cells());
    }
}
