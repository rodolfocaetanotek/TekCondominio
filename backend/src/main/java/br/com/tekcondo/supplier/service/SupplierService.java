package br.com.tekcondo.supplier.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
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
    private final AcessoService accessService;
    private final CondominiumAccess access;
    private final AuditoriaService auditService;

    public SupplierService(SupplierRepository repository, AcessoService accessService, CondominiumAccess access,
                           AuditoriaService auditService) {
        this.repository = repository;
        this.accessService = accessService;
        this.access = access;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SupplierResponse> list(String search, Boolean active, Pageable pageable) {
        UUID orgId = access.organizationId();
        // Fornecedores são consultados também por quem lança despesas.
        if (accessService.concessoesAtuais().escopo(Permissao.SUPPLIER_VIEW).isVazio()) {
            accessService.exigirEscopo(Permissao.FINANCE_VIEW);
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
        return PaginaResponse.of(repository.findAll(spec, pageable), SupplierService::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        accessService.exigirEscopo(Permissao.SUPPLIER_VIEW);
        return toResponse(load(id));
    }

    @Transactional
    public SupplierResponse save(UUID id, SupplierRequest request, Boolean active) {
        accessService.exigirEscopo(Permissao.SUPPLIER_EDIT);
        Supplier supplier = id == null ? new Supplier(access.organizationId()) : load(id);
        String document = DocumentosBrasileiros.normalizarDocumento(request.document());
        if (document != null) {
            repository.findByOrganizationIdAndDocument(access.organizationId(), document)
                    .filter(s -> !s.getId().equals(supplier.getId()))
                    .ifPresent(s -> {
                        throw ApiException.conflito("SUPPLIER_DOCUMENT_TAKEN", "Já existe o fornecedor " + s.getName() + " com este CPF/CNPJ.");
                    });
        }
        supplier.update(request.name(), document, blank(request.category()), blank(request.email()),
                DocumentosBrasileiros.somenteDigitos(request.phone()), blank(request.contactName()), blank(request.notes()));
        if (active != null) {
            supplier.setActive(active);
        }
        repository.save(supplier);
        auditService.registrar(id == null ? AcaoAuditoria.SUPPLIER_CREATED : AcaoAuditoria.SUPPLIER_UPDATED, "Supplier",
                supplier.getId(), Map.of("name", supplier.getName(), "active", supplier.isActive()));
        return toResponse(supplier);
    }

    private Supplier load(UUID id) {
        return repository.findByIdAndOrganizationId(id, access.organizationId()).orElseThrow(() -> ApiException.naoEncontrado("Fornecedor"));
    }

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getDocument(), s.getCategory(), s.getEmail(), s.getPhone(),
                s.getContactName(), s.getNotes(), s.isActive(), s.getVersao());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
