package br.com.tekcondo.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.billing.entity.Agreement;
import br.com.tekcondo.billing.entity.Allocation;
import br.com.tekcondo.billing.entity.CollectionNote;
import br.com.tekcondo.billing.service.AllocationCalculator;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoResumo;
import br.com.tekcondo.financeiro.entity.Lancamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BillingDtos {

    private BillingDtos() {
    }

    // ---------- rateio / geração de cobranças
    public record AllocationRequest(
            @NotBlank(message = "Informe a competência.") @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Competência no formato AAAA-MM.") String competence,
            @NotBlank(message = "Informe a descrição.") @Size(max = 200) String description,
            @NotNull(message = "Informe a conta de receita.") UUID chartAccountId,
            @NotNull(message = "Informe o critério de rateio.") AllocationCalculator.Method method,
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            LocalDate dueDate,
            Map<UUID, BigDecimal> consumption) {
    }

    public record AllocationLine(UUID unitId, String unitLabel, BigDecimal idealFraction, String responsibleName, BigDecimal amount) {
    }

    public record AllocationPreview(LocalDate competence, LocalDate dueDate, BigDecimal total, int units,
                                    int unitsWithoutResponsible, List<AllocationLine> lines) {
    }

    public record AllocationResponse(UUID id, LocalDate competence, String description, String accountCode, String accountName,
                                     AllocationCalculator.Method method, BigDecimal baseAmount, LocalDate dueDate,
                                     int unitsCount, BigDecimal generatedAmount, Allocation.Status status, long paidCount,
                                     BigDecimal receivedAmount, String cancelReason, Instant createdAt) {
    }

    // ---------- inadimplência
    public record DelinquencyRow(UUID unitId, String unitLabel, String responsibleName, String responsibleEmail,
                                 String responsiblePhone, int overdueCount, BigDecimal principal, BigDecimal charges,
                                 BigDecimal total, LocalDate oldestDueDate, long maxDaysLate, LocalDate lastContactDate) {
    }

    public record DelinquencyReport(LocalDate asOf, BigDecimal totalPrincipal, BigDecimal totalCharges, BigDecimal total,
                                    BigDecimal issuedLast12Months, BigDecimal delinquencyRate, int unitsInDebt,
                                    long activeUnits, List<DelinquencyRow> rows) {
    }

    public record DebtLine(UUID entryId, String description, LocalDate competence, LocalDate dueDate, BigDecimal amount,
                           BigDecimal paid, BigDecimal remaining, Lancamento.Situacao status, long daysLate,
                           BigDecimal fine, BigDecimal interest, BigDecimal updatedTotal) {
    }

    public record NoteRequest(@NotNull(message = "Informe o tipo de contato.") CollectionNote.ContactType contactType,
                              @NotNull(message = "Informe a data do contato.") LocalDate contactDate,
                              @NotBlank(message = "Descreva o contato.") @Size(max = 1000) String note,
                              UUID entryId) {
    }

    public record NoteResponse(UUID id, CollectionNote.ContactType contactType, LocalDate contactDate, String note,
                               UUID entryId, String createdByName, Instant createdAt) {

        public static NoteResponse from(CollectionNote n) {
            return new NoteResponse(n.getId(), n.getContactType(), n.getContactDate(), n.getNote(), n.getEntryId(),
                    n.getCreatedByName(), n.getCreatedAt());
        }
    }

    public record UnitDebt(UUID unitId, String unitLabel, String responsibleName, LocalDate asOf, BigDecimal openPrincipal,
                           BigDecimal openCharges, BigDecimal openTotal, List<DebtLine> lines, List<NoteResponse> notes,
                           List<AgreementResponse> agreements) {
    }

    // ---------- acordos
    public record AgreementRequest(
            @NotNull(message = "Informe a unidade.") UUID unitId,
            @NotEmpty(message = "Selecione os débitos a renegociar.") List<UUID> entryIds,
            @Min(value = 1, message = "Mínimo de 1 parcela.") @Max(value = 120, message = "Máximo de 120 parcelas.") int installments,
            @NotNull(message = "Informe o vencimento da 1ª parcela.") LocalDate firstDueDate,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal discount,
            boolean includeCharges,
            @Size(max = 500) String notes) {
    }

    public record AgreementResponse(UUID id, UUID unitId, String unitLabel, BigDecimal originalAmount, BigDecimal chargesAmount,
                                    BigDecimal discount, BigDecimal totalAmount, int installments, LocalDate firstDueDate,
                                    Agreement.Status status, long paidInstallments, String notes, String cancelReason,
                                    Instant createdAt, List<LancamentoResumo> installmentEntries) {
    }
}
