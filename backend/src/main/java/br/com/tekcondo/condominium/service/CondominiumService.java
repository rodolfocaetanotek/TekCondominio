package br.com.tekcondo.condominium.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.dto.CondominiumRequest;
import br.com.tekcondo.condominium.dto.CondominiumResponse;
import br.com.tekcondo.condominium.dto.CondominiumSummary;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.mapper.CondominiumMapper;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
import br.com.tekcondo.condominium.repository.CondominiumSpecifications;
import br.com.tekcondo.acesso.autorizacao.EscopoAcesso;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CondominiumService {

    private static final String ENTITY = "Condominium";

    private final CondominiumRepository repository;
    private final CondominiumMapper mapper;
    private final AcessoService accessService;
    private final UsuarioAtualProvider currentUserProvider;
    private final AuditoriaService auditService;
    private final Clock clock;

    public CondominiumService(CondominiumRepository repository, CondominiumMapper mapper, AcessoService accessService,
                              UsuarioAtualProvider currentUserProvider, AuditoriaService auditService, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.accessService = accessService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CondominiumSummary> list(String search, CondominiumStatus status, Pageable pageable) {
        UsuarioAtual user = currentUserProvider.exigirMembroAdministradora();
        EscopoAcesso scope = accessService.exigirEscopo(Permissao.CONDOMINIUM_VIEW);
        Specification<Condominium> spec = Specification.allOf(
                CondominiumSpecifications.naAdministradora(user.idAdministradora()),
                CondominiumSpecifications.withinScope(scope),
                CondominiumSpecifications.matching(search),
                CondominiumSpecifications.withStatus(status));
        return PaginaResponse.of(repository.findAll(spec, pageable), mapper::toSummary);
    }

    /** Condomínios em que o usuário tem alguma permissão, com as permissões efetivas em cada um. */
    @Transactional(readOnly = true)
    public java.util.List<br.com.tekcondo.condominium.dto.CondominiumOption> accessible() {
        UsuarioAtual user = currentUserProvider.exigirMembroAdministradora();
        br.com.tekcondo.acesso.autorizacao.ConcessoesUsuario grants = accessService.concessoesAtuais();
        java.util.Set<Permissao> orgWide = grants.administradoraInteira();
        java.util.List<Condominium> condominiums = !orgWide.isEmpty()
                ? repository.findAll(CondominiumSpecifications.naAdministradora(user.idAdministradora()),
                        org.springframework.data.domain.Sort.by("name"))
                : repository.findAllById(grants.porCondominio().keySet()).stream()
                        .filter(c -> c.getOrganizationId().equals(user.idAdministradora()))
                        .sorted(java.util.Comparator.comparing(Condominium::getName)).toList();
        return condominiums.stream().map(c -> {
            java.util.Set<Permissao> perms = java.util.EnumSet.noneOf(Permissao.class);
            perms.addAll(orgWide);
            perms.addAll(grants.porCondominio().getOrDefault(c.getId(), java.util.Set.of()));
            return new br.com.tekcondo.condominium.dto.CondominiumOption(c.getId(), c.getName(), c.getStatus(), perms);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CondominiumResponse get(UUID id) {
        Condominium condominium = load(id);
        accessService.exigir(Permissao.CONDOMINIUM_VIEW, id);
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse create(CondominiumRequest request) {
        UsuarioAtual user = currentUserProvider.exigirMembroAdministradora();
        accessService.exigirNaAdministradora(Permissao.CONDOMINIUM_CREATE);
        String cnpj = DocumentosBrasileiros.normalizarCnpj(request.cnpj());
        if (cnpj != null && repository.existsByOrganizationIdAndCnpj(user.idAdministradora(), cnpj)) {
            throw duplicateCnpj();
        }
        Condominium condominium = new Condominium(user.idAdministradora(), user.idUsuario());
        apply(condominium, request, cnpj, user.idUsuario());
        repository.save(condominium);
        auditService.registrar(AcaoAuditoria.CONDOMINIUM_CREATED, ENTITY, condominium.getId(), snapshot(condominium));
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse update(UUID id, CondominiumRequest request) {
        UsuarioAtual user = currentUserProvider.exigirMembroAdministradora();
        Condominium condominium = load(id);
        accessService.exigir(Permissao.CONDOMINIUM_EDIT, id);
        if (request.version() == null) {
            throw ApiException.requisicaoInvalida("VERSION_REQUIRED", "Informe a versão do registro para atualizá-lo.");
        }
        if (!request.version().equals(condominium.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        String cnpj = DocumentosBrasileiros.normalizarCnpj(request.cnpj());
        if (cnpj != null && repository.existsByOrganizationIdAndCnpjAndIdNot(user.idAdministradora(), cnpj, id)) {
            throw duplicateCnpj();
        }
        Map<String, Object> before = snapshot(condominium);
        apply(condominium, request, cnpj, user.idUsuario());
        repository.flush();
        Map<String, Object> changes = diff(before, snapshot(condominium));
        if (!changes.isEmpty()) {
            auditService.registrar(AcaoAuditoria.CONDOMINIUM_UPDATED, ENTITY, id, changes);
        }
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse inactivate(UUID id, String reason) {
        UsuarioAtual user = currentUserProvider.exigirMembroAdministradora();
        Condominium condominium = load(id);
        accessService.exigir(Permissao.CONDOMINIUM_INACTIVATE, id);
        condominium.inactivate(reason.trim(), Instant.now(clock), user.idUsuario());
        repository.flush();
        auditService.registrar(AcaoAuditoria.CONDOMINIUM_INACTIVATED, ENTITY, id, Map.of("reason", reason.trim()));
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse activate(UUID id) {
        UsuarioAtual user = currentUserProvider.exigirMembroAdministradora();
        Condominium condominium = load(id);
        accessService.exigir(Permissao.CONDOMINIUM_INACTIVATE, id);
        condominium.activate(user.idUsuario());
        repository.flush();
        auditService.registrar(AcaoAuditoria.CONDOMINIUM_ACTIVATED, ENTITY, id, null);
        return mapper.toResponse(condominium);
    }

    /** Carrega o condomínio sempre restrito à organização do usuário (outra organização = 404). */
    private Condominium load(UUID id) {
        UUID organizationId = currentUserProvider.exigirMembroAdministradora().idAdministradora();
        return repository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.naoEncontrado("Condomínio"));
    }

    private void apply(Condominium condominium, CondominiumRequest request, String cnpj, UUID userId) {
        condominium.updateDetails(request.name().trim(), CondominiumMapper.trim(request.legalName()), cnpj,
                request.type(), mapper.toAddress(request.address()), CondominiumMapper.trim(request.email()),
                DocumentosBrasileiros.somenteDigitos(request.phone()), userId);
    }

    private static ApiException duplicateCnpj() {
        return ApiException.conflito("CONDOMINIUM_CNPJ_TAKEN", "Já existe um condomínio com este CNPJ na sua administradora.");
    }

    private static Map<String, Object> snapshot(Condominium c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", c.getName());
        map.put("legalName", c.getLegalName());
        map.put("cnpj", c.getCnpj());
        map.put("type", c.getType() == null ? null : c.getType().name());
        map.put("address", c.getAddress() == null ? null : c.getAddress().toString());
        map.put("email", c.getEmail());
        map.put("phone", c.getPhone());
        return map;
    }

    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", oldValue);
                change.put("to", newValue);
                changes.put(field, change);
            }
        });
        return changes;
    }
}
