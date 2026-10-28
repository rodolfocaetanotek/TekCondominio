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

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.billing.entity.BankReturnFile;
import br.com.tekcondo.billing.entity.BankReturnItem;
import br.com.tekcondo.billing.repository.BankReturnFileRepository;
import br.com.tekcondo.billing.repository.BankReturnItemRepository;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoDetalhe;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.BaixaRequest;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.Baixa;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.financeiro.service.LancamentoService;
import br.com.tekcondo.financeiro.service.ConfiguracaoFinanceiraService;
import br.com.tekcondo.financeiro.service.EncargosCalculator;
import br.com.tekcondo.financeiro.service.BloqueioPeriodo;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.banco.RetornoCnab240Parser;
import br.com.tekcondo.integracao.banco.OfxParser;
import br.com.tekcondo.comum.exception.ApiException;
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
    private final LancamentoRepository entries;
    private final LancamentoService entryService;
    private final ConfiguracaoFinanceiraService setup;
    private final BloqueioPeriodo periodLock;
    private final AcessoCondominio access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public BankReturnService(BankReturnFileRepository files, BankReturnItemRepository items, LancamentoRepository entries,
                             LancamentoService entryService, ConfiguracaoFinanceiraService setup, BloqueioPeriodo periodLock, AcessoCondominio access,
                             AuditoriaService auditService, Clock clock) {
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
        Condominio condominium = access.exigirAtivo(condominiumId, Permissao.BILLING_EDIT);
        access.exigir(condominiumId, Permissao.FINANCE_EDIT);
        ContaBancaria account = setup.carregarContaBancaria(condominiumId, bankAccountId);
        if (!account.isAtivo()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta bancária está inativa.");
        }
        String hash = OfxParser.sha256(content);
        if (files.existsByBankAccountIdAndFileHash(bankAccountId, hash)) {
            throw ApiException.conflito("RETURN_ALREADY_PROCESSED", "Este arquivo de retorno já foi processado nesta conta.");
        }
        RetornoCnab240Parser.Retorno parsed = RetornoCnab240Parser.parse(content);
        if (account.getCodigoBanco() != null && !strip(account.getCodigoBanco()).equals(strip(parsed.codigoBanco()))) {
            throw ApiException.regraNegocio("RETURN_BANK_MISMATCH", "O retorno é do banco " + parsed.codigoBanco()
                    + ", mas a conta " + account.getNome() + " é do banco " + account.getCodigoBanco() + ".");
        }
        var user = access.usuario();
        BankReturnFile file = files.save(new BankReturnFile(condominium.getIdAdministradora(), condominiumId, bankAccountId,
                safeName(fileName), hash, parsed.codigoBanco(), parsed.dataGeracao(), parsed.itens().size(), user.idUsuario(), user.nome()));
        int settled = 0;
        int pending = 0;
        for (RetornoCnab240Parser.Item parsedItem : parsed.itens()) {
            BankReturnItem item = new BankReturnItem(condominium.getIdAdministradora(), condominiumId, bankAccountId, file.getId(), parsedItem);
            if (parsedItem.isLiquidacao()) {
                if (items.alreadySettled(bankAccountId, item.getMovementCode(), item.getOccurrenceDate(), item.getOurNumber(), item.getYourNumber())) {
                    item.pending(null, "Liquidação já baixada em retorno anterior (não baixada de novo).");
                } else {
                    Optional<Lancamento> entry = locate(condominiumId, item);
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
        details.put("bank", parsed.codigoBanco());
        details.put("items", parsed.itens().size());
        details.put("settled", settled);
        details.put("pending", pending);
        auditService.registrar(AcaoAuditoria.BANK_RETURN_PROCESSED, "BankAccount", bankAccountId, details);
        return toSummary(file);
    }

    @Transactional(readOnly = true)
    public List<FileSummary> files(UUID condominiumId) {
        access.exigir(condominiumId, Permissao.BILLING_VIEW);
        return files.findTop30ByCondominiumIdOrderByCreatedAtDesc(condominiumId).stream().map(BankReturnService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemView> items(UUID condominiumId, UUID fileId) {
        access.exigir(condominiumId, Permissao.BILLING_VIEW);
        BankReturnFile file = files.findById(fileId).filter(f -> fileBelongs(f, condominiumId))
                .orElseThrow(() -> ApiException.naoEncontrado("Arquivo de retorno"));
        return items.findByFileIdOrderBySequenceAsc(file.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemView> pending(UUID condominiumId) {
        access.exigir(condominiumId, Permissao.BILLING_VIEW);
        return items.findByStatus(condominiumId, BankReturnItem.Status.PENDING).stream().map(this::toView).toList();
    }

    /** Baixa manual de uma pendência na cobrança escolhida pelo usuário (ex.: pagador usou boleto antigo). */
    @Transactional
    public ItemView settleManually(UUID condominiumId, UUID itemId, SettleRequest request) {
        access.exigirAtivo(condominiumId, Permissao.BILLING_EDIT);
        access.exigir(condominiumId, Permissao.FINANCE_EDIT);
        BankReturnItem item = load(condominiumId, itemId);
        if (item.getStatus() != BankReturnItem.Status.PENDING) {
            throw ApiException.regraNegocio("RETURN_ITEM_NOT_PENDING", "Este item do retorno não está pendente.");
        }
        if (items.alreadySettled(item.getBankAccountId(), item.getMovementCode(), item.getOccurrenceDate(),
                item.getOurNumber(), item.getYourNumber())) {
            throw ApiException.conflito("RETURN_ALREADY_SETTLED", "Esta liquidação já foi baixada em outro retorno.");
        }
        Lancamento entry = entryService.carregar(condominiumId, request.entryId());
        ContaBancaria account = setup.carregarContaBancaria(condominiumId, item.getBankAccountId());
        settle(condominiumId, account, item, entry);
        if (item.getStatus() != BankReturnItem.Status.SETTLED) {
            throw ApiException.regraNegocio("RETURN_ITEM_NOT_SETTLED", item.getMessage());
        }
        auditService.registrar(AcaoAuditoria.BANK_RETURN_ITEM_RESOLVED, "BankReturnItem", itemId, Map.of("entryId", entry.getId().toString()));
        return toView(item);
    }

    @Transactional
    public ItemView ignore(UUID condominiumId, UUID itemId, IgnoreRequest request) {
        access.exigirAtivo(condominiumId, Permissao.BILLING_EDIT);
        BankReturnItem item = load(condominiumId, itemId);
        item.ignore(request.note().trim(), access.usuario().idUsuario(), Instant.now(clock));
        auditService.registrar(AcaoAuditoria.BANK_RETURN_ITEM_RESOLVED, "BankReturnItem", itemId, Map.of("ignored", request.note().trim()));
        return toView(item);
    }

    // ------------------------------------------------------------------

    private Optional<Lancamento> locate(UUID condominiumId, BankReturnItem item) {
        for (String candidate : new String[]{item.getYourNumber(), item.getOurNumber()}) {
            Long number = number(candidate);
            if (number != null) {
                Optional<Lancamento> found = entries.findByIdCondominioAndNumeroCobranca(condominiumId, number)
                        .filter(e -> e.getNatureza() == Lancamento.Natureza.INCOME);
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
    private void settle(UUID condominiumId, ContaBancaria account, BankReturnItem item, Lancamento entry) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = item.settlementDate();
        if (date != null && date.isAfter(today)) {
            date = item.getOccurrenceDate();
        }
        LocalDate closed = periodLock.fechadoAte(condominiumId);
        String problem = null;
        if (entry.getSituacao() != Lancamento.Situacao.OPEN && entry.getSituacao() != Lancamento.Situacao.PARTIAL) {
            problem = "A cobrança nº " + entry.getNumeroCobranca() + " não está em aberto (" + entry.getSituacao() + ").";
        } else if (date == null || date.isAfter(today)) {
            problem = "Retorno sem data de pagamento válida.";
        } else if (date.isBefore(account.getDataAbertura())) {
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
        if (principal.compareTo(entry.valorAberto()) > 0) {
            // Pago a maior: o excedente é tratado como encargo recebido, mantendo o principal igual ao saldo do título.
            fees = fees.add(principal.subtract(entry.valorAberto()));
            principal = entry.valorAberto();
            note += " (pago a maior)";
        }
        EncargosCalculator.Calculo quote = EncargosCalculator.calcular(principal, entry.getDataVencimento(), date, entry.getPercentualMulta(),
                entry.getPercentualJurosMensal());
        BigDecimal fine = fees.min(quote.multa());
        BigDecimal interest = fees.subtract(fine);
        LancamentoDetalhe detail = entryService.baixar(condominiumId, entry.getId(), new BaixaRequest(account.getId(), date, principal, fine,
                interest, item.getDiscount(), Baixa.FormaPagamento.BOLETO, note));
        item.settled(entry.getId(), detail.baixas().getLast().id());
    }

    private BankReturnItem load(UUID condominiumId, UUID itemId) {
        return items.findInCondominium(itemId, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Item do retorno"));
    }

    private static boolean fileBelongs(BankReturnFile f, UUID condominiumId) {
        return f.getCondominiumId().equals(condominiumId);
    }

    private ItemView toView(BankReturnItem i) {
        Lancamento e = i.getEntryId() == null ? null : entries.findById(i.getEntryId()).orElse(null);
        return new ItemView(i.getId(), i.getFileId(), i.getSequence(), i.getMovementCode(), movementLabel(i.getMovementCode()),
                i.getOurNumber(), i.getYourNumber(), i.getDueDate(), i.getFaceValue(), i.getFees(), i.getDiscount(), i.getPaidAmount(),
                i.getBankFee(), i.getOccurrenceDate(), i.getCreditDate(), i.getStatus(), i.getEntryId(),
                e == null ? null : e.getDescricao(), e == null || e.getUnidade() == null ? null : e.getUnidade().rotulo(), i.getMessage());
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
