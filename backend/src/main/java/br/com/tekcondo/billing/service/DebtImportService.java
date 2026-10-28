package br.com.tekcondo.billing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.billing.dto.BillingDtos.DebtImportResult;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.ChartAccountRepository;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.finance.service.PeriodLock;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.people.repository.OccupancyRepository;
import br.com.tekcondo.shared.io.BrazilianNumbers;
import br.com.tekcondo.shared.io.CsvReader;
import br.com.tekcondo.structure.dto.StructureDtos.ImportError;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importa débitos em aberto vindos de outro sistema. Colunas: bloco; unidade*; competencia* (MM/AAAA);
 * vencimento* (DD/MM/AAAA); valor*; descricao; documento; conta (código do plano de contas, padrão 1.01).
 * Tudo ou nada: com qualquer erro, nada é gravado.
 */
@Service
public class DebtImportService {

    private static final int MAX_LINES = 5000;
    private static final String DEFAULT_ACCOUNT = "1.01";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/uuuu");

    private final CondominiumAccess access;
    private final UnitRepository units;
    private final OccupancyRepository occupancies;
    private final ChartAccountRepository chartAccounts;
    private final FinancialEntryRepository entries;
    private final PeriodLock periodLock;
    private final AuditService auditService;

    public DebtImportService(CondominiumAccess access, UnitRepository units, OccupancyRepository occupancies,
                             ChartAccountRepository chartAccounts, FinancialEntryRepository entries, PeriodLock periodLock,
                             AuditService auditService) {
        this.access = access;
        this.units = units;
        this.occupancies = occupancies;
        this.chartAccounts = chartAccounts;
        this.entries = entries;
        this.periodLock = periodLock;
        this.auditService = auditService;
    }

    private record Parsed(Unit unit, LocalDate competence, LocalDate dueDate, BigDecimal amount, String description,
                          String document, ChartAccount account) {
    }

    @Transactional
    public DebtImportResult importDebts(UUID condominiumId, byte[] content, boolean dryRun) {
        Condominium condominium = access.requireActive(condominiumId, Permission.BILLING_EDIT);
        List<CsvReader.Row> rows = CsvReader.read(content);
        List<ImportError> errors = new ArrayList<>();
        List<String> header = CsvReader.header(content);
        if (!header.containsAll(List.of("unidade", "competencia", "vencimento", "valor"))) {
            errors.add(new ImportError(1, "Cabeçalho inválido: as colunas unidade, competencia, vencimento e valor são obrigatórias."));
            return new DebtImportResult(true, rows.size(), 0, BigDecimal.ZERO, errors);
        }
        if (rows.size() > MAX_LINES) {
            errors.add(new ImportError(1, "Arquivo com mais de " + MAX_LINES + " linhas."));
            return new DebtImportResult(true, rows.size(), 0, BigDecimal.ZERO, errors);
        }

        Map<String, ChartAccount> accounts = chartAccounts.findAvailable(condominium.getOrganizationId()).stream()
                .filter(a -> a.isActive() && a.isAnalytic() && a.getNature() == ChartAccount.Nature.INCOME)
                .collect(Collectors.toMap(ChartAccount::getCode, Function.identity(), (a, b) -> a));
        LocalDate closedUntil = periodLock.closedUntil(condominiumId);
        Set<String> documents = new HashSet<>();
        List<Parsed> parsed = new ArrayList<>();

        for (CsvReader.Row row : rows) {
            int line = row.line();
            int errorsBefore = errors.size();
            Unit unit = findUnit(condominiumId, row.get("bloco"), row.get("unidade"), line, errors);
            LocalDate competence = parseMonth(row.get("competencia"), line, errors);
            LocalDate dueDate = parseDate(row.get("vencimento"), line, errors);
            BigDecimal amount = parseAmount(row.get("valor"), line, errors);
            String description = row.get("descricao") == null ? "Débito importado" : row.get("descricao");
            String document = row.get("documento");
            String accountCode = row.get("conta") == null ? DEFAULT_ACCOUNT : row.get("conta");
            ChartAccount account = accounts.get(accountCode);

            if (account == null) {
                errors.add(new ImportError(line, "Conta de receita não encontrada: " + accountCode));
            }
            if (description.length() > 200 || (document != null && document.length() > 40)) {
                errors.add(new ImportError(line, "Descrição (até 200) ou documento (até 40 caracteres) muito longos."));
            }
            if (competence != null && closedUntil != null && !competence.isAfter(closedUntil)) {
                errors.add(new ImportError(line, "Competência em período já fechado."));
            }
            if (document != null && (!documents.add(document)
                    || entries.existsByCondominiumIdAndOriginAndDocumentNumber(condominiumId, FinancialEntry.Origin.IMPORT, document))) {
                errors.add(new ImportError(line, "Documento já importado: " + document));
            }
            if (errors.size() == errorsBefore) {
                parsed.add(new Parsed(unit, competence, dueDate, amount, description, document, account));
            }
        }

        BigDecimal total = parsed.stream().map(Parsed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!errors.isEmpty() || dryRun) {
            return new DebtImportResult(true, rows.size(), errors.isEmpty() ? parsed.size() : 0,
                    errors.isEmpty() ? total : BigDecimal.ZERO, errors);
        }

        UUID by = access.user().userId();
        for (Parsed p : parsed) {
            FinancialEntry entry = new FinancialEntry(condominium.getOrganizationId(), condominiumId,
                    FinancialEntry.Kind.INCOME, FinancialEntry.Origin.IMPORT, by);
            entry.details(p.description(), p.account(), null, null, p.unit(), responsible(p.unit()), p.amount(),
                    p.dueDate(), p.competence(), p.document(), null, by);
            entry.lateFees(condominium.getFinePercent(), condominium.getInterestMonthlyPercent());
            entries.save(entry);
        }
        auditService.record(AuditAction.DEBTS_IMPORTED, "Condominium", condominiumId,
                Map.of("debts", parsed.size(), "total", total.toPlainString()));
        return new DebtImportResult(false, rows.size(), parsed.size(), total, List.of());
    }

    private Unit findUnit(UUID condominiumId, String building, String identifier, int line, List<ImportError> errors) {
        if (identifier == null) {
            errors.add(new ImportError(line, "Unidade não informada."));
            return null;
        }
        List<Unit> found = units.findByIdentifier(condominiumId, identifier).stream()
                .filter(u -> building == null ? u.getBuilding() == null
                        : u.getBuilding() != null && u.getBuilding().getName().equalsIgnoreCase(building))
                .toList();
        if (found.isEmpty()) {
            errors.add(new ImportError(line, "Unidade não cadastrada: " + (building == null ? "" : building + " ") + identifier));
            return null;
        }
        return found.getFirst();
    }

    private Person responsible(Unit unit) {
        return occupancies.findCurrentFinancialResponsible(unit.getId()).map(Occupancy::getPerson)
                .or(() -> occupancies.findCurrentByUnits(List.of(unit.getId())).stream()
                        .filter(o -> o.getRole() == Occupancy.Role.OWNER).map(Occupancy::getPerson).findFirst())
                .orElse(null);
    }

    private static LocalDate parseMonth(String raw, int line, List<ImportError> errors) {
        try {
            return YearMonth.parse(require(raw), MONTH).atDay(1);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            errors.add(new ImportError(line, "Competência inválida (use MM/AAAA): " + raw));
            return null;
        }
    }

    private static LocalDate parseDate(String raw, int line, List<ImportError> errors) {
        try {
            return LocalDate.parse(require(raw), DATE);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            errors.add(new ImportError(line, "Vencimento inválido (use DD/MM/AAAA): " + raw));
            return null;
        }
    }

    private static BigDecimal parseAmount(String raw, int line, List<ImportError> errors) {
        BigDecimal amount = null;
        try {
            amount = BrazilianNumbers.parse(require(raw));
        } catch (IllegalArgumentException ignored) {
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            errors.add(new ImportError(line, "Valor inválido: " + raw));
            return null;
        }
        return amount;
    }

    private static String require(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException();
        }
        return raw;
    }
}
