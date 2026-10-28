package br.com.tekcondo.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.EntryPayment;
import br.com.tekcondo.finance.entity.FinancialEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    // ---------- parâmetros
    public record Settings(
            @NotNull @DecimalMin(value = "0", message = "Multa não pode ser negativa.")
            @DecimalMax(value = "2", message = "A multa é limitada a 2% (CC, art. 1.336, §1º).") @Digits(integer = 1, fraction = 2) BigDecimal finePercent,
            @NotNull @DecimalMin(value = "0") @DecimalMax(value = "10", message = "Juros de até 10% ao mês.") @Digits(integer = 2, fraction = 2) BigDecimal interestMonthlyPercent,
            @Min(value = 1, message = "Dia de vencimento entre 1 e 28.") @Max(value = 28, message = "Dia de vencimento entre 1 e 28.") int billingDueDay,
            boolean requirePayableApproval) {
    }

    // ---------- plano de contas
    public record ChartAccountRequest(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String name,
            @NotNull(message = "Informe a natureza.") ChartAccount.Nature nature) {
    }

    public record ChartAccountResponse(UUID id, String code, String name, ChartAccount.Nature nature, boolean analytic,
                                       boolean system, boolean active) {

        public static ChartAccountResponse from(ChartAccount a) {
            return new ChartAccountResponse(a.getId(), a.getCode(), a.getName(), a.getNature(), a.isAnalytic(), a.isSystem(),
                    a.isActive());
        }
    }

    // ---------- centros de custo e contas bancárias
    public record NameRequest(@NotBlank(message = "Informe o nome.") @Size(max = 80) String name) {
    }

    public record CostCenterResponse(UUID id, String name, boolean active) {
    }

    public record BankAccountRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 80) String name,
            @NotNull(message = "Informe o tipo.") BankAccount.Kind kind,
            @Size(max = 5) String bankCode,
            @Size(max = 10) String agency,
            @Size(max = 20) String accountNumber,
            @Digits(integer = 13, fraction = 2) BigDecimal openingBalance,
            LocalDate openingDate) {
    }

    public record BankAccountResponse(UUID id, String name, BankAccount.Kind kind, String bankCode, String agency,
                                      String accountNumber, BigDecimal openingBalance, LocalDate openingDate,
                                      boolean active, BigDecimal balance) {
    }

    // ---------- lançamentos
    public record EntryRequest(
            @NotNull(message = "Informe se é receita ou despesa.") FinancialEntry.Kind kind,
            @NotBlank(message = "Informe a descrição.") @Size(max = 200) String description,
            @NotNull(message = "Informe a conta do plano de contas.") UUID chartAccountId,
            UUID costCenterId,
            UUID supplierId,
            UUID unitId,
            UUID personId,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 13, fraction = 2, message = "Valor com no máximo 2 casas decimais.") BigDecimal amount,
            @NotNull(message = "Informe o vencimento.") LocalDate dueDate,
            LocalDate competence,
            @Size(max = 40) String documentNumber,
            @Size(max = 500) String notes,
            @Min(value = 1, message = "Mínimo de 1 parcela.") @Max(value = 60, message = "Máximo de 60 parcelas/recorrências.") Integer repeat,
            Long version) {
    }

    public record EntrySummary(UUID id, FinancialEntry.Kind kind, String description, String accountCode,
                               String accountName, String supplierName, String unitLabel, String personName,
                               BigDecimal amount, BigDecimal paidAmount, BigDecimal remaining, LocalDate dueDate,
                               LocalDate competence, FinancialEntry.Status status, FinancialEntry.Approval approval,
                               boolean overdue, String installmentLabel, String documentNumber) {
    }

    public record PaymentResponse(UUID id, UUID bankAccountId, String bankAccountName, LocalDate paidOn,
                                  BigDecimal principal, BigDecimal fine, BigDecimal interest, BigDecimal discount,
                                  BigDecimal total, EntryPayment.Method method, String notes, boolean reconciled,
                                  boolean reversed, Instant reversedAt, String reversalReason) {

        public static PaymentResponse from(EntryPayment p) {
            return new PaymentResponse(p.getId(), p.getBankAccount().getId(), p.getBankAccount().getName(), p.getPaidOn(),
                    p.getPrincipal(), p.getFine(), p.getInterest(), p.getDiscount(), p.getTotal(), p.getMethod(),
                    p.getNotes(), p.getReconciledAt() != null, p.isReversed(), p.getReversedAt(), p.getReversalReason());
        }
    }

    public record EntryDetail(EntrySummary summary, UUID chartAccountId, UUID costCenterId, String costCenterName,
                              UUID supplierId, UUID unitId, UUID personId, String notes, FinancialEntry.Origin origin,
                              String approvalNote, Instant approvedAt, String cancelReason, Instant cancelledAt,
                              BigDecimal finePercent, BigDecimal interestMonthlyPercent, Long version,
                              List<PaymentResponse> payments) {
    }

    public record DecisionRequest(@Size(max = 255) String note) {
    }

    public record ReasonRequest(@NotBlank(message = "Informe a justificativa.") @Size(min = 5, max = 255,
            message = "A justificativa deve ter entre 5 e 255 caracteres.") String reason) {
    }

    public record PaymentRequest(
            @NotNull(message = "Informe a conta de movimentação.") UUID bankAccountId,
            @NotNull(message = "Informe a data do pagamento.") LocalDate paidOn,
            @NotNull(message = "Informe o valor principal.") @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero.")
            @Digits(integer = 13, fraction = 2) BigDecimal principal,
            @DecimalMin(value = "0") @Digits(integer = 13, fraction = 2) BigDecimal fine,
            @DecimalMin(value = "0") @Digits(integer = 13, fraction = 2) BigDecimal interest,
            @DecimalMin(value = "0") @Digits(integer = 13, fraction = 2) BigDecimal discount,
            @NotNull(message = "Informe a forma de pagamento.") EntryPayment.Method method,
            @Size(max = 255) String notes) {
    }

    public record PaymentQuote(BigDecimal principal, long daysLate, BigDecimal fine, BigDecimal interest, BigDecimal total) {
    }

    // ---------- transferências, extrato e conciliação
    public record TransferRequest(
            @NotNull(message = "Informe a conta de origem.") UUID fromAccountId,
            @NotNull(message = "Informe a conta de destino.") UUID toAccountId,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero.")
            @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull(message = "Informe a data.") LocalDate date,
            @Size(max = 200) String description) {
    }

    public record TransferResponse(UUID id, UUID fromAccountId, String fromAccountName, UUID toAccountId,
                                   String toAccountName, BigDecimal amount, LocalDate date, String description,
                                   boolean reconciled, boolean reversed, String reversalReason) {
    }

    public record StatementLine(String type, UUID id, LocalDate date, String description, BigDecimal amount,
                                BigDecimal balance, boolean reconciled) {
    }

    public record Statement(UUID bankAccountId, String bankAccountName, LocalDate from, LocalDate to,
                            BigDecimal openingBalance, BigDecimal closingBalance, BigDecimal reconciledBalance,
                            List<StatementLine> lines) {
    }

    public record ReconcileRequest(@NotEmpty(message = "Selecione ao menos uma movimentação.") List<@Valid ReconcileItem> items,
                                   boolean reconciled) {
    }

    public record ReconcileItem(@NotBlank String type, @NotNull UUID id) {
    }

    // ---------- orçamento
    public record BudgetCell(@NotNull UUID chartAccountId, @Min(1) @Max(12) int month,
                             @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal amount) {
    }

    public record BudgetRequest(@NotNull List<@Valid BudgetCell> cells) {
    }

    public record BudgetRow(UUID chartAccountId, String code, String name, ChartAccount.Nature nature,
                            List<BigDecimal> budgeted, List<BigDecimal> realized, BigDecimal totalBudgeted,
                            BigDecimal totalRealized) {
    }

    public record Budget(int year, List<BudgetRow> rows) {
    }
}
