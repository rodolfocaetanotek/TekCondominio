package br.com.tekcondo.fornecedor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.fornecedor.dto.FornecedorDtos.FornecedorRequest;
import br.com.tekcondo.fornecedor.dto.FornecedorDtos.FornecedorResponse;
import br.com.tekcondo.fornecedor.entity.Fornecedor;
import br.com.tekcondo.fornecedor.repository.FornecedorRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FornecedorService {

    private final FornecedorRepository repository;
    private final AcessoService acessoService;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoria;

    public FornecedorService(FornecedorRepository repository, AcessoService acessoService, AcessoCondominio acesso,
                             AuditoriaService auditoria) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.acesso = acesso;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<FornecedorResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        UUID idAdministradora = acesso.idAdministradora();
        // Fornecedores são consultados também por quem lança despesas.
        if (acessoService.concessoesAtuais().escopo(Permissao.SUPPLIER_VIEW).isVazio()) {
            acessoService.exigirEscopo(Permissao.FINANCE_VIEW);
        }
        Specification<Fornecedor> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("idAdministradora"), idAdministradora));
            if (busca != null && !busca.isBlank()) {
                String termo = busca.trim().toLowerCase(Locale.ROOT);
                String digitos = termo.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
                p.add(cb.or(cb.like(cb.lower(root.get("nome")), "%" + termo + "%"),
                        cb.like(cb.lower(cb.coalesce(root.get("categoria"), "")), "%" + termo + "%"),
                        cb.like(cb.coalesce(root.get("documento"), ""), "%" + (digitos.isEmpty() ? termo : digitos) + "%")));
            }
            if (ativo != null) {
                p.add(cb.equal(root.get("ativo"), ativo));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PaginaResponse.of(repository.findAll(spec, pageable), FornecedorService::paraResponse);
    }

    @Transactional(readOnly = true)
    public FornecedorResponse buscar(UUID id) {
        acessoService.exigirEscopo(Permissao.SUPPLIER_VIEW);
        return paraResponse(carregar(id));
    }

    @Transactional
    public FornecedorResponse salvar(UUID id, FornecedorRequest request, Boolean ativo) {
        acessoService.exigirEscopo(Permissao.SUPPLIER_EDIT);
        Fornecedor fornecedor = id == null ? new Fornecedor(acesso.idAdministradora()) : carregar(id);
        String documento = DocumentosBrasileiros.normalizarDocumento(request.documento());
        if (documento != null) {
            repository.findByIdAdministradoraAndDocumento(acesso.idAdministradora(), documento)
                    .filter(s -> !s.getId().equals(fornecedor.getId()))
                    .ifPresent(s -> {
                        throw ApiException.conflito("SUPPLIER_DOCUMENT_TAKEN", "Já existe o fornecedor " + s.getNome() + " com este CPF/CNPJ.");
                    });
        }
        fornecedor.atualizar(request.nome(), documento, aparadoOuNulo(request.categoria()), aparadoOuNulo(request.email()),
                DocumentosBrasileiros.somenteDigitos(request.telefone()), aparadoOuNulo(request.nomeContato()),
                aparadoOuNulo(request.observacoes()));
        if (ativo != null) {
            fornecedor.setAtivo(ativo);
        }
        repository.save(fornecedor);
        auditoria.registrar(id == null ? AcaoAuditoria.SUPPLIER_CREATED : AcaoAuditoria.SUPPLIER_UPDATED, "Supplier",
                fornecedor.getId(), Map.of("name", fornecedor.getNome(), "active", fornecedor.isAtivo()));
        return paraResponse(fornecedor);
    }

    private Fornecedor carregar(UUID id) {
        return repository.findByIdAndIdAdministradora(id, acesso.idAdministradora()).orElseThrow(() -> ApiException.naoEncontrado("Fornecedor"));
    }

    private static FornecedorResponse paraResponse(Fornecedor s) {
        return new FornecedorResponse(s.getId(), s.getNome(), s.getDocumento(), s.getCategoria(), s.getEmail(), s.getTelefone(),
                s.getNomeContato(), s.getObservacoes(), s.isAtivo(), s.getVersao());
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
