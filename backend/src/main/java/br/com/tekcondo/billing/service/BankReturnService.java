package br.com.tekcondo.billing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.billing.entity.BankReturnFile;
import br.com.tekcondo.billing.entity.BankReturnItem;
import br.com.tekcondo.billing.repository.BankReturnFileRepository;
import br.com.tekcondo.billing.repository.BankReturnItemRepository;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.dto.FinanceDtos.EntryDetail;
import br.com.tekcondo.finance.dto.FinanceDtos.PaymentRequest;
import br.com.tekcondo.finance.entity.BankAccount;
import br.com.tekcondo.finance.entity.EntryPayment;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.finance.service.EntryService;
import br.com.tekcondo.finance.service.FinanceSetupService;
import br.com.tekcondo.finance.service.LateFeeCalculator;
import br.com.tekcondo.finance.service.PeriodLock;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.integration.bank.Cnab240ReturnParser;
import br.com.tekcondo.integration.bank.OfxParser;
import br.com.tekcondo.shared.exception.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processa o arquivo de retorno de cobrança (CNAB 240): cada liquidação é localizada pelo número de cobrança do
 * título e baixada automaticamente, com multa/juros separados conforme os parâmetros fotografados no título. O que
 * não puder ser baixado com segurança fica pendente para tratamento manual — nada é "forçado".
 */
@Service
public class BankReturnService {

    public record FileSummary(UUID id, UUID bankAccountId, String fileName, String bankCode, LocalDate generatedOn, int itemsTotal,
                              int settled, int pending, Instant createdAt, String createdByName) {
    }

    public record ItemView(UUID id, UUID fileId, int sequence, String movementCode, String movementLabel, String ourNumber,
                           String yourNumber, LocalDate dueDate, BigDecimal faceValue, BigDecimal fees, BigDecimal discount,
                           BigDecimal paidAmount, BigDecimal bankFee, LocalDate occurrenceDate, LocalDate creditDate,
                           BankReturnItem.Status status, UUID entryId, String entryDescription, String unitLabel, String message) {
    }

    public record SettleRequest(@NotNull(message = "Informe a cobrança.") UUID entryId) {
    }

    public record IgnoreRequest(@NotBlank(message = "Informe o motivo.") @Size(min = 3, max = 255) String note) {
    }

    private final BankReturnFileRepository files;
    private final BankReturnItemRepository items;
    private final FinancialEntryRepository entries;
    private final EntryService entryService;
    private final FinanceSetupService setup;
    private final PeriodLock periodLock;
    private final CondominiumAccess access;
    private final AuditService auditService;
    private final Clock clock;

    public BankReturnService(BankReturnFileRepository files, BankReturnItemRepository items, FinancialEntryRepository entries,
                             EntryService entryService, FinanceSetupService setup, PeriodLock periodLock, CondominiumAccess access,
                             AuditService auditService, Clock clock) {
        this.files = files;
        this.items = items;
        this.entries = entries;
        this.entryService = entryService;
        this.setup = setup;
        this.periodLock = periodLock;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public FileSummary process(UUID condominiumId, UUID bankAccountId, String fileName, byte[] content) {
        Condominium condominium = access.requireActive(condominiumId, Permission.BILLING_EDIT);
        access.require(condominiumId, Permission.FINANCE_EDIT);
        BankAccount account = setup.loadBankAccount(condominiumId, bankAccountId);
        if (!account.isActive()) {
            throw ApiException.businessRule("BANK_ACCOUNT_INACTIVE", "A conta bancária está inativa.");
        }
        String hash = OfxParser.sha256(content);
        if (files.existsByBankAccountIdAndFileHash(bankAccountId, hash)) {
            throw ApiException.conflict("RETURN_ALREADY_PROCESSED", "Este arquivo de retorno já foi processado nesta conta.");
        }
        Cnab240ReturnParser.ReturnFile parsed = Cnab240ReturnParser.parse(content);
        if (account.getBankCode() != null && !strip(account.getBankCode()).equals(strip(parsed.bankCode()))) {
            throw ApiException.businessRule("RETURN_BANK_MISMATCH", "O retorno é do banco " + parsed.bankCode()
                    + ", mas a conta " + account.getName() + " é do banco " + account.getBankCode() + ".");
        }
        var user = access.user();
        BankReturnFile file = files.save(new BankReturnFile(condominium.getOrganizationId(), condominiumId, bankAccountId,
                safeName(fileName), hash, parsed.bankCode(), parsed.generatedOn(), parsed.items().size(), user.userId(), user.name()));
        int settled = 0;
        int pending = 0;
        for (Cnab240ReturnParser.Item parsedItem : parsed.items()) {
            BankReturnItem item = new BankReturnItem(condominium.getOrganizationId(), condominiumId, bankAccountId, file.getId(), parsedItem);
            if (parsedItem.isSettlement()) {
                if (items.alreadySettled(bankAccountId, item.getMovementCode(), item.getOccurrenceDate(), item.getOurNumber(), item.getYourNumber())) {
                    item.pending(null, "Liquidação já baixada em retorno anterior (não baixada de novo).");
                } else {
                    Optional<FinancialEntry> entry = locate(condominiumId, item);
                    if (entry.isEmpty()) {
                        item.pending(null, "Cobrança não localizada pelo número informado ao banco.");
                    } else {
                        settle(condominiumId, account, item, entry.get());
                    }
                }
                if (item.getStatus() == BankReturnItem.Status.SETTLED) settled++;
                else pending++;
            } else {
                item.info(movementLabel(item.getMovementCode()));
            }
            items.save(item);
        }
        file.result(settled, pending);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("file", file.getFileName());
        details.put("bank", parsed.bankCode());
        details.put("items", parsed.items().size());
        details.put("settled", settled);
        details.put("pending", pending);
        auditService.record(AuditAction.BANK_RETURN_PROCESSED, "BankAccount", bankAccountId, details);
        return toSummary(file);
    }

    @Transactional(readOnly = true)
    public List<FileSummary> files(UUID condominiumId) {
        access.require(condominiumId, Permission.BILLING_VIEW);
        return files.findTop30ByCondominiumIdOrderByCreatedAtDesc(condominiumId).stream().map(BankReturnService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemView> items(UUID condominiumId, UUID fileId) {
        access.require(condominiumId, Permission.BILLING_VIEW);
        BankReturnFile file = files.findById(fileId).filter(f -> fileBelongs(f, condominiumId))
                .orElseThrow(() -> ApiException.notFound("Arquivo de retorno"));
        return items.findByFileIdOrderBySequenceAsc(file.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemView> pending(UUID condominiumId) {
        access.require(condominiumId, Permission.BILLING_VIEW);
        return items.findByStatus(condominiumId, BankReturnItem.Status.PENDING).stream().map(this::toView).toList();
    }

    /** Baixa manual de uma pendência na cobrança escolhida pelo usuário (ex.: pagador usou boleto antigo). */
    @Transactional
    public ItemView settleManually(UUID condominiumId, UUID itemId, SettleRequest request) {
        access.requireActive(condominiumId, Permission.BILLING_EDIT);
        access.require(condominiumId, Permission.FINANCE_EDIT);
        BankReturnItem item = load(condominiumId, itemId);
        if (item.getStatus() != BankReturnItem.Status.PENDING) {
            throw ApiException.businessRule("RETURN_ITEM_NOT_PENDING", "Este item do retorno não está pendente.");
        }
        if (items.alreadySettled(item.getBankAccountId(), item.getMovementCode(), item.getOccurrenceDate(),
                item.getOurNumber(), item.getYourNumber())) {
            throw ApiException.conflict("RETURN_ALREADY_SETTLED", "Esta liquidação já foi baixada em outro retorno.");
        }
        FinancialEntry entry = entryService.load(condominiumId, request.entryId());
        BankAccount account = setup.loadBankAccount(condominiumId, item.getBankAccountId());
        settle(condominiumId, account, item, entry);
        if (item.getStatus() != BankReturnItem.Status.SETTLED) {
            throw ApiException.businessRule("RETURN_ITEM_NOT_SETTLED", item.getMessage());
        }
        auditService.record(AuditAction.BANK_RETURN_ITEM_RESOLVED, "BankReturnItem", itemId, Map.of("entryId", entry.getId().toString()));
        return toView(item);
    }

    @Transactional
    public ItemView ignore(UUID condominiumId, UUID itemId, IgnoreRequest request) {
        access.requireActive(condominiumId, Permission.BILLING_EDIT);
        BankReturnItem item = load(condominiumId, itemId);
        item.ignore(request.note().trim(), access.user().userId(), Instant.now(clock));
        auditService.record(AuditAction.BANK_RETURN_ITEM_RESOLVED, "BankReturnItem", itemId, Map.of("ignored", request.note().trim()));
        return toView(item);
    }

    // ------------------------------------------------------------------

    private Optional<FinancialEntry> locate(UUID condominiumId, BankReturnItem item) {
        for (String candidate : new String[]{item.getYourNumber(), item.getOurNumber()}) {
            Long number = number(candidate);
            if (number != null) {
                Optional<FinancialEntry> found = entries.findByCondominiumIdAndCollectionNumber(condominiumId, number)
                        .filter(e -> e.getKind() == FinancialEntry.Kind.INCOME);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Todas as regras são verificadas ANTES da baixa: uma exceção dentro da transação a marcaria para rollback e
     * perderia o processamento do arquivo inteiro. O que não passar vira pendência com o motivo.
     */
    private void settle(UUID condominiumId, BankAccount account, BankReturnItem item, FinancialEntry entry) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = item.settlementDate();
        if (date != null && date.isAfter(today)) {
            date = item.getOccurrenceDate();
        }
        LocalDate closed = periodLock.closedUntil(condominiumId);
        String problem = null;
        if (entry.getStatus() != FinancialEntry.Status.OPEN && entry.getStatus() != FinancialEntry.Status.PARTIAL) {
            problem = "A cobrança nº " + entry.getCollectionNumber() + " não está em aberto (" + entry.getStatus() + ").";
        } else if (date == null || date.isAfter(today)) {
            problem = "Retorno sem data de pagamento válida.";
        } else if (date.isBefore(account.getOpeningDate())) {
            problem = "Data de pagamento anterior à abertura da conta bancária.";
        } else if (closed != null && !date.isAfter(closed)) {
            problem = "Pagamento em período financeiro fechado; reabra o período para baixar.";
        }
        BigDecimal principal = item.getPaidAmount().subtract(item.getFees()).add(item.getDiscount());
        BigDecimal fees = item.getFees();
        if (problem == null && principal.signum() <= 0) {
            problem = "Valor pago no retorno não cobre o principal.";
        }
        if (problem != null) {
            item.pending(entry.getId(), problem);
            return;
        }
        String note = "Retorno CNAB — seq. " + item.getSequence();
        if (principal.compareTo(entry.remaining()) > 0) {
            // Pago a maior: o excedente é tratado como encargo recebido, mantendo o principal igual ao saldo do título.
            fees = fees.add(principal.subtract(entry.remaining()));
            principal = entry.remaining();
            note += " (pago a maior)";
        }
        LateFeeCalculator.Quote quote = LateFeeCalculator.quote(principal, entry.getDueDate(), date, entry.getFinePercent(),
                entry.getInterestMonthlyPercent());
        BigDecimal fine = fees.min(quote.fine());
        BigDecimal interest = fees.subtract(fine);
        EntryDetail detail = entryService.pay(condominiumId, entry.getId(), new PaymentRequest(account.getId(), date, principal, fine,
                interest, item.getDiscount(), EntryPayment.Method.BOLETO, note));
        item.settled(entry.getId(), detail.payments().getLast().id());
    }

    private BankReturnItem load(UUID condominiumId, UUID itemId) {
        return items.findInCondominium(itemId, condominiumId).orElseThrow(() -> ApiException.notFound("Item do retorno"));
    }

    private static boolean fileBelongs(BankReturnFile f, UUID condominiumId) {
        return f.getCondominiumId().equals(condominiumId);
    }

    private ItemView toView(BankReturnItem i) {
        FinancialEntry e = i.getEntryId() == null ? null : entries.findById(i.getEntryId()).orElse(null);
        return new ItemView(i.getId(), i.getFileId(), i.getSequence(), i.getMovementCode(), movementLabel(i.getMovementCode()),
                i.getOurNumber(), i.getYourNumber(), i.getDueDate(), i.getFaceValue(), i.getFees(), i.getDiscount(), i.getPaidAmount(),
                i.getBankFee(), i.getOccurrenceDate(), i.getCreditDate(), i.getStatus(), i.getEntryId(),
                e == null ? null : e.getDescription(), e == null || e.getUnit() == null ? null : e.getUnit().label(), i.getMessage());
    }

    private static FileSummary toSummary(BankReturnFile f) {
        return new FileSummary(f.getId(), f.getBankAccountId(), f.getFileName(), f.getBankCode(), f.getGeneratedOn(), f.getItemsTotal(),
                f.getSettled(), f.getPending(), f.getCreatedAt(), f.getCreatedByName());
    }

    /** Códigos de movimento de retorno mais comuns (FEBRABAN, nota G044). */
    static String movementLabel(String code) {
        return switch (code) {
            case "02" -> "Entrada confirmada";
            case "03" -> "Entrada rejeitada";
            case "06" -> "Liquidação";
            case "09" -> "Baixa";
            case "12" -> "Abatimento concedido";
            case "14" -> "Vencimento alterado";
            case "17" -> "Liquidação após baixa";
            case "19" -> "Protesto confirmado";
            case "28" -> "Débito de tarifas/custas";
            default -> "Movimento " + code;
        };
    }

    private static Long number(String s) {
        if (s == null) return null;
        String digits = s.replaceAll("\\D", "").replaceFirst("^0+", "");
        if (digits.isEmpty() || digits.length() > 18) return null;
        return Long.parseLong(digits);
    }

    private static String strip(String bank) {
        String d = bank.replaceAll("\\D", "").replaceFirst("^0+", "");
        return d.isEmpty() ? bank : d;
    }

    private static String safeName(String name) {
        String n = name == null || name.isBlank() ? "retorno.ret" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
