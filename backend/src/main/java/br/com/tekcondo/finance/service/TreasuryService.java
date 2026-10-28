package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.finance.dto.FinanceDtos.ReconcileItem;
import br.com.tekcondo.finance.dto.FinanceDtos.Statement;
import br.com.tekcondo.finance.dto.FinanceDtos.StatementLine;
import br.com.tekcondo.finance.dto.FinanceDtos.TransferRequest;
import br.com.tekcondo.finance.dto.FinanceDtos.TransferResponse;
import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.entity.BankTransfer;
import br.com.tekcondo.finance.entity.EntryPayment;
import br.com.tekcondo.finance.repository.BankTransferRepository;
import br.com.tekcondo.finance.repository.EntryPaymentRepository;
import br.com.tekcondo.finance.repository.StatementLineRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transferências entre contas, extrato com saldo e conciliação bancária. */
@Service
public class TreasuryService {

    private final BankTransferRepository transfers;
    private final EntryPaymentRepository payments;
    private final StatementLineRepository statementLines;
    private final FinanceSetupService setup;
    private final FinanceQueries queries;
    private final PeriodLock periodLock;
    private final AcessoCondominio access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public TreasuryService(BankTransferRepository transfers, EntryPaymentRepository payments,
                           StatementLineRepository statementLines, FinanceSetupService setup,
                           FinanceQueries queries, PeriodLock periodLock, AcessoCondominio access, AuditoriaService auditService, Clock clock) {
        this.transfers = transfers;
        this.payments = payments;
        this.statementLines = statementLines;
        this.setup = setup;
        this.queries = queries;
        this.periodLock = periodLock;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> transfers(UUID condominiumId) {
        access.exigir(condominiumId, Permissao.FINANCE_VIEW);
        return transfers.findByCondominium(condominiumId).stream().map(TreasuryService::toResponse).toList();
    }

    @Transactional
    public TransferResponse transfer(UUID condominiumId, TransferRequest request) {
        Condominio condominium = access.exigirAtivo(condominiumId, Permissao.FINANCE_EDIT);
        BankAccount from = active(condominiumId, request.fromAccountId());
        BankAccount to = active(condominiumId, request.toAccountId());
        if (request.date().isAfter(LocalDate.now(clock))) {
            throw ApiException.regraNegocio("FUTURE_TRANSFER", "A data da transferência não pode ser futura.");
        }
        periodLock.ensureOpen(condominiumId, request.date());
        BigDecimal available = queries.balances(condominiumId, request.date()).getOrDefault(from.getId(), from.getOpeningBalance());
        if (from.getKind() == BankAccount.Kind.CASH && available.compareTo(request.amount()) < 0) {
            throw ApiException.regraNegocio("INSUFFICIENT_CASH", "Saldo de caixa insuficiente na data (R$ " + available.toPlainString() + ").");
        }
        BankTransfer transfer = transfers.save(new BankTransfer(condominium.getIdAdministradora(), condominiumId, from, to,
                request.amount(), request.date(), blank(request.description()), access.usuario().idUsuario()));
        auditService.registrar(AcaoAuditoria.TRANSFER_CREATED, "BankTransfer", transfer.getId(), Map.of("from", from.getName(),
                "to", to.getName(), "amount", request.amount().toPlainString(), "date", request.date().toString()));
        return toResponse(transfer);
    }

    @Transactional
    public TransferResponse reverseTransfer(UUID condominiumId, UUID id, String reason) {
        access.exigir(condominiumId, Permissao.FINANCE_CANCEL);
        BankTransfer transfer = transfers.findByIdAndCondominiumId(id, condominiumId)
                .orElseThrow(() -> ApiException.naoEncontrado("Transferência"));
        periodLock.ensureOpen(condominiumId, transfer.getTransferDate());
        transfer.reverse(reason.trim(), access.usuario().idUsuario(), Instant.now(clock));
        auditService.registrar(AcaoAuditoria.TRANSFER_REVERSED, "BankTransfer", id, Map.of("reason", reason.trim()));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public Statement statement(UUID condominiumId, UUID accountId, LocalDate from, LocalDate to) {
        access.exigir(condominiumId, Permissao.FINANCE_VIEW);
        BankAccount account = setup.loadBankAccount(condominiumId, accountId);
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "A data inicial deve ser anterior à final.");
        }
        BigDecimal opening = queries.balanceBefore(accountId, account.getOpeningBalance(), start);
        List<StatementLine> lines = queries.movements(accountId, opening, start, end);
        BigDecimal closing = lines.isEmpty() ? opening : lines.getLast().balance();
        return new Statement(accountId, account.getName(), start, end, opening, closing,
                queries.reconciledBalance(accountId, account.getOpeningBalance(), end), lines);
    }

    /** Marca/desmarca movimentações como conferidas com o extrato do banco. */
    @Transactional
    public int reconcile(UUID condominiumId, List<ReconcileItem> items, boolean reconciled) {
        access.exigirAtivo(condominiumId, Permissao.FINANCE_EDIT);
        UUID by = access.usuario().idUsuario();
        Instant now = Instant.now(clock);
        List<UUID> paymentIds = items.stream().filter(i -> "PAYMENT".equals(i.type())).map(ReconcileItem::id).toList();
        List<UUID> transferIds = items.stream().filter(i -> i.type().startsWith("TRANSFER")).map(ReconcileItem::id).distinct().toList();
        List<EntryPayment> foundPayments = paymentIds.isEmpty() ? List.of() : payments.findAllInCondominium(paymentIds, condominiumId);
        List<BankTransfer> foundTransfers = transferIds.isEmpty() ? List.of() : transfers.findAllInCondominium(transferIds, condominiumId);
        if (foundPayments.size() != paymentIds.size() || foundTransfers.size() != transferIds.size()) {
            throw ApiException.naoEncontrado("Movimentação");
        }
        periodLock.ensureOpen(condominiumId, java.util.stream.Stream.concat(foundPayments.stream().map(EntryPayment::getPaidOn),
                foundTransfers.stream().map(BankTransfer::getTransferDate)).toArray(LocalDate[]::new));
        foundPayments.forEach(p -> p.reconcile(reconciled, by, now));
        foundTransfers.forEach(t -> t.reconcile(reconciled, by, now));
        if (!reconciled) {
            // A linha do extrato importado casada com a movimentação volta a ficar pendente.
            foundPayments.forEach(p -> statementLines.findByPaymentId(p.getId()).ifPresent(br.com.tekcondo.finance.entity.StatementLine::reset));
            foundTransfers.forEach(t -> {
                statementLines.findByBankAccountIdAndTransferId(t.getFromAccount().getId(), t.getId()).ifPresent(br.com.tekcondo.finance.entity.StatementLine::reset);
                statementLines.findByBankAccountIdAndTransferId(t.getToAccount().getId(), t.getId()).ifPresent(br.com.tekcondo.finance.entity.StatementLine::reset);
            });
        }
        int count = foundPayments.size() + foundTransfers.size();
        auditService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "Condominium", condominiumId,
                Map.of("items", count, "reconciled", reconciled));
        return count;
    }

    private BankAccount active(UUID condominiumId, UUID id) {
        BankAccount account = setup.loadBankAccount(condominiumId, id);
        if (!account.isActive()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta " + account.getName() + " está inativa.");
        }
        return account;
    }

    private static TransferResponse toResponse(BankTransfer t) {
        return new TransferResponse(t.getId(), t.getFromAccount().getId(), t.getFromAccount().getName(),
                t.getToAccount().getId(), t.getToAccount().getName(), t.getAmount(), t.getTransferDate(),
                t.getDescription(), t.getReconciledAt() != null, t.getReversedAt() != null, t.getReversalReason());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
