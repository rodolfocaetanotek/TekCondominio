package br.com.tekcondo.supplier.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.identity.access.AccessService;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.validation.BrazilianDocuments;
import br.com.tekcondo.shared.web.PageResponse;
import br.com.tekcondo.supplier.dto.SupplierDtos.SupplierRequest;
import br.com.tekcondo.supplier.dto.SupplierDtos.SupplierResponse;
import br.com.tekcondo.supplier.entity.Supplier;
import br.com.tekcondo.supplier.repository.SupplierRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository repository;
    private final AccessService accessService;
    private final CondominiumAccess access;
    private final AuditService auditService;

    public SupplierService(SupplierRepository repository, AccessService accessService, CondominiumAccess access,
                           AuditService auditService) {
        this.repository = repository;
        this.accessService = accessService;
        this.access = access;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(String search, Boolean active, Pageable pageable) {
        UUID orgId = access.organizationId();
        // Fornecedores são consultados também por quem lança despesas.
        if (accessService.currentGrants().scope(Permission.SUPPLIER_VIEW).isEmpty()) {
            accessService.requireScope(Permission.FINANCE_VIEW);
        }
        Specification<Supplier> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));
            if (search != null && !search.isBlank()) {
                String term = search.trim().toLowerCase(Locale.ROOT);
                String digits = term.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
                p.add(cb.or(cb.like(cb.lower(root.get("name")), "%" + term + "%"),
                        cb.like(cb.lower(cb.coalesce(root.get("category"), "")), "%" + term + "%"),
                        cb.like(cb.coalesce(root.get("document"), ""), "%" + (digits.isEmpty() ? term : digits) + "%")));
            }
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), SupplierService::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        accessService.requireScope(Permission.SUPPLIER_VIEW);
        return toResponse(load(id));
    }

    @Transactional
    public SupplierResponse save(UUID id, SupplierRequest request, Boolean active) {
        accessService.requireScope(Permission.SUPPLIER_EDIT);
        Supplier supplier = id == null ? new Supplier(access.organizationId()) : load(id);
        String document = BrazilianDocuments.normalizeDocument(request.document());
        if (document != null) {
            repository.findByOrganizationIdAndDocument(access.organizationId(), document)
                    .filter(s -> !s.getId().equals(supplier.getId()))
                    .ifPresent(s -> {
                        throw ApiException.conflict("SUPPLIER_DOCUMENT_TAKEN", "Já existe o fornecedor " + s.getName() + " com este CPF/CNPJ.");
                    });
        }
        supplier.update(request.name(), document, blank(request.category()), blank(request.email()),
                BrazilianDocuments.digitsOnly(request.phone()), blank(request.contactName()), blank(request.notes()));
        if (active != null) {
            supplier.setActive(active);
        }
        repository.save(supplier);
        auditService.record(id == null ? AuditAction.SUPPLIER_CREATED : AuditAction.SUPPLIER_UPDATED, "Supplier",
                supplier.getId(), Map.of("name", supplier.getName(), "active", supplier.isActive()));
        return toResponse(supplier);
    }

    private Supplier load(UUID id) {
        return repository.findByIdAndOrganizationId(id, access.organizationId()).orElseThrow(() -> ApiException.notFound("Fornecedor"));
    }

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getDocument(), s.getCategory(), s.getEmail(), s.getPhone(),
                s.getContactName(), s.getNotes(), s.isActive(), s.getVersion());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
