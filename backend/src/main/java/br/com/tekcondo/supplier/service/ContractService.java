package br.com.tekcondo.supplier.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.supplier.entity.Contract;
import br.com.tekcondo.supplier.entity.ContractHistory;
import br.com.tekcondo.supplier.entity.Supplier;
import br.com.tekcondo.supplier.repository.ContractHistoryRepository;
import br.com.tekcondo.supplier.repository.ContractRepository;
import br.com.tekcondo.supplier.repository.SupplierRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {

    public record Request(@NotNull(message = "Informe o fornecedor.") UUID supplierId,
                          @NotBlank(message = "Informe o objeto do contrato.") @Size(max = 200) String title,
                          @Size(max = 2000) String description,
                          @NotNull(message = "Informe o valor.") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal amount,
                          @NotNull Contract.Periodicity periodicity,
                          @NotNull(message = "Informe o início da vigência.") LocalDate startDate,
                          LocalDate endDate,
                          @Size(max = 20) String readjustmentIndex,
                          @Min(1) @Max(12) Integer readjustmentMonth,
                          @Min(0) @Max(365) Integer noticeDays,
                          Long version) {
    }

    public record TerminateRequest(@NotNull(message = "Informe a data de encerramento.") LocalDate terminatedOn,
                                   @NotBlank(message = "Informe o motivo.") @Size(max = 255) String reason) {
    }

    public record History(String change, Instant createdAt, String createdByName) {
    }

    public record Response(UUID id, UUID supplierId, String supplierName, String title, String description, BigDecimal amount,
                           Contract.Periodicity periodicity, LocalDate startDate, LocalDate endDate, String readjustmentIndex,
                           Integer readjustmentMonth, int noticeDays, Contract.Status status, boolean expiring, boolean expired,
                           LocalDate terminatedOn, String terminationReason, Long version, List<History> history) {
    }

    private final ContractRepository contracts;
    private final ContractHistoryRepository history;
    private final SupplierRepository suppliers;
    private final CondominiumAccess access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public ContractService(ContractRepository contracts, ContractHistoryRepository history, SupplierRepository suppliers,
                           CondominiumAccess access, AuditoriaService auditService, Clock clock) {
        this.contracts = contracts;
        this.history = history;
        this.suppliers = suppliers;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID condominiumId) {
        access.require(condominiumId, Permissao.SUPPLIER_VIEW);
        return contracts.findByCondominium(condominiumId).stream().map(c -> toResponse(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID condominiumId, UUID id) {
        access.require(condominiumId, Permissao.SUPPLIER_VIEW);
        return toResponse(load(condominiumId, id), true);
    }

    @Transactional
    public Response save(UUID condominiumId, UUID id, Request r) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.SUPPLIER_EDIT);
        Supplier supplier = suppliers.findByIdAndOrganizationId(r.supplierId(), condominium.getOrganizationId())
                .orElseThrow(() -> ApiException.naoEncontrado("Fornecedor"));
        Contract contract;
        List<String> changes = new ArrayList<>();
        if (id == null) {
            contract = new Contract(condominium.getOrganizationId(), condominiumId);
            changes.add("Contrato cadastrado");
        } else {
            contract = load(condominiumId, id);
            if (r.version() == null || !r.version().equals(contract.getVersao())) {
                throw ApiException.versaoObsoleta();
            }
            diff(changes, "Fornecedor", contract.getSupplier().getName(), supplier.getName());
            diff(changes, "Valor", contract.getAmount() == null ? null : contract.getAmount().toPlainString(), r.amount().toPlainString());
            diff(changes, "Término", contract.getEndDate(), r.endDate());
            diff(changes, "Início", contract.getStartDate(), r.startDate());
            diff(changes, "Objeto", contract.getTitle(), r.title().trim());
        }
        contract.update(supplier, r.title().trim(), blank(r.description()), r.amount(), r.periodicity(), r.startDate(), r.endDate(),
                blank(r.readjustmentIndex()), r.readjustmentMonth(), r.noticeDays() == null ? 30 : r.noticeDays());
        contracts.save(contract);
        contracts.flush();
        if (!changes.isEmpty()) {
            history.save(new ContractHistory(contract.getId(), String.join("; ", changes), access.user().nome()));
        }
        auditService.registrar(id == null ? AcaoAuditoria.CONTRACT_CREATED : AcaoAuditoria.CONTRACT_UPDATED, "Contract", contract.getId(),
                Map.of("title", contract.getTitle(), "changes", String.join("; ", changes)));
        return toResponse(contract, true);
    }

    @Transactional
    public Response terminate(UUID condominiumId, UUID id, TerminateRequest r) {
        access.require(condominiumId, Permissao.SUPPLIER_EDIT);
        Contract contract = load(condominiumId, id);
        contract.terminate(r.terminatedOn(), r.reason().trim());
        history.save(new ContractHistory(contract.getId(), "Encerrado em " + r.terminatedOn() + ": " + r.reason().trim(), access.user().nome()));
        auditService.registrar(AcaoAuditoria.CONTRACT_TERMINATED, "Contract", id, Map.of("reason", r.reason().trim()));
        return toResponse(contract, true);
    }

    private Contract load(UUID condominiumId, UUID id) {
        return contracts.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Contrato"));
    }

    private Response toResponse(Contract c, boolean withHistory) {
        LocalDate today = LocalDate.now(clock);
        List<History> h = withHistory ? history.findByContractIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(x -> new History(x.getChange(), x.getCreatedAt(), x.getCreatedByName())).toList() : List.of();
        return new Response(c.getId(), c.getSupplier().getId(), c.getSupplier().getName(), c.getTitle(), c.getDescription(),
                c.getAmount(), c.getPeriodicity(), c.getStartDate(), c.getEndDate(), c.getReadjustmentIndex(),
                c.getReadjustmentMonth(), c.getNoticeDays(), c.getStatus(), c.isExpiring(today), c.isExpired(today),
                c.getTerminatedOn(), c.getTerminationReason(), c.getVersao(), h);
    }

    private static void diff(List<String> changes, String label, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(label + ": " + (before == null ? "—" : before) + " → " + (after == null ? "—" : after));
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
