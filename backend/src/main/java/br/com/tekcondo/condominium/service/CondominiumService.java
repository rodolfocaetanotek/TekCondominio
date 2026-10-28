package br.com.tekcondo.condominium.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.condominium.dto.CondominiumRequest;
import br.com.tekcondo.condominium.dto.CondominiumResponse;
import br.com.tekcondo.condominium.dto.CondominiumSummary;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.mapper.CondominiumMapper;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
import br.com.tekcondo.condominium.repository.CondominiumSpecifications;
import br.com.tekcondo.identity.access.AccessScope;
import br.com.tekcondo.identity.access.AccessService;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import br.com.tekcondo.shared.validation.BrazilianDocuments;
import br.com.tekcondo.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CondominiumService {

    private static final String ENTITY = "Condominium";

    private final CondominiumRepository repository;
    private final CondominiumMapper mapper;
    private final AccessService accessService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    public CondominiumService(CondominiumRepository repository, CondominiumMapper mapper, AccessService accessService,
                              CurrentUserProvider currentUserProvider, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.accessService = accessService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<CondominiumSummary> list(String search, CondominiumStatus status, Pageable pageable) {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        AccessScope scope = accessService.requireScope(Permission.CONDOMINIUM_VIEW);
        Specification<Condominium> spec = Specification.allOf(
                CondominiumSpecifications.inOrganization(user.organizationId()),
                CondominiumSpecifications.withinScope(scope),
                CondominiumSpecifications.matching(search),
                CondominiumSpecifications.withStatus(status));
        return PageResponse.of(repository.findAll(spec, pageable), mapper::toSummary);
    }

    /** Condomínios em que o usuário tem alguma permissão, com as permissões efetivas em cada um. */
    @Transactional(readOnly = true)
    public java.util.List<br.com.tekcondo.condominium.dto.CondominiumOption> accessible() {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        br.com.tekcondo.identity.access.UserGrants grants = accessService.currentGrants();
        java.util.Set<Permission> orgWide = grants.organizationWide();
        java.util.List<Condominium> condominiums = !orgWide.isEmpty()
                ? repository.findAll(CondominiumSpecifications.inOrganization(user.organizationId()),
                        org.springframework.data.domain.Sort.by("name"))
                : repository.findAllById(grants.byCondominium().keySet()).stream()
                        .filter(c -> c.getOrganizationId().equals(user.organizationId()))
                        .sorted(java.util.Comparator.comparing(Condominium::getName)).toList();
        return condominiums.stream().map(c -> {
            java.util.Set<Permission> perms = java.util.EnumSet.noneOf(Permission.class);
            perms.addAll(orgWide);
            perms.addAll(grants.byCondominium().getOrDefault(c.getId(), java.util.Set.of()));
            return new br.com.tekcondo.condominium.dto.CondominiumOption(c.getId(), c.getName(), c.getStatus(), perms);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CondominiumResponse get(UUID id) {
        Condominium condominium = load(id);
        accessService.require(Permission.CONDOMINIUM_VIEW, id);
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse create(CondominiumRequest request) {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        accessService.requireOrganizationWide(Permission.CONDOMINIUM_CREATE);
        String cnpj = BrazilianDocuments.normalizeCnpj(request.cnpj());
        if (cnpj != null && repository.existsByOrganizationIdAndCnpj(user.organizationId(), cnpj)) {
            throw duplicateCnpj();
        }
        Condominium condominium = new Condominium(user.organizationId(), user.userId());
        apply(condominium, request, cnpj, user.userId());
        repository.save(condominium);
        auditService.record(AuditAction.CONDOMINIUM_CREATED, ENTITY, condominium.getId(), snapshot(condominium));
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse update(UUID id, CondominiumRequest request) {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        Condominium condominium = load(id);
        accessService.require(Permission.CONDOMINIUM_EDIT, id);
        if (request.version() == null) {
            throw ApiException.badRequest("VERSION_REQUIRED", "Informe a versão do registro para atualizá-lo.");
        }
        if (!request.version().equals(condominium.getVersion())) {
            throw ApiException.staleVersion();
        }
        String cnpj = BrazilianDocuments.normalizeCnpj(request.cnpj());
        if (cnpj != null && repository.existsByOrganizationIdAndCnpjAndIdNot(user.organizationId(), cnpj, id)) {
            throw duplicateCnpj();
        }
        Map<String, Object> before = snapshot(condominium);
        apply(condominium, request, cnpj, user.userId());
        repository.flush();
        Map<String, Object> changes = diff(before, snapshot(condominium));
        if (!changes.isEmpty()) {
            auditService.record(AuditAction.CONDOMINIUM_UPDATED, ENTITY, id, changes);
        }
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse inactivate(UUID id, String reason) {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        Condominium condominium = load(id);
        accessService.require(Permission.CONDOMINIUM_INACTIVATE, id);
        condominium.inactivate(reason.trim(), Instant.now(clock), user.userId());
        repository.flush();
        auditService.record(AuditAction.CONDOMINIUM_INACTIVATED, ENTITY, id, Map.of("reason", reason.trim()));
        return mapper.toResponse(condominium);
    }

    @Transactional
    public CondominiumResponse activate(UUID id) {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        Condominium condominium = load(id);
        accessService.require(Permission.CONDOMINIUM_INACTIVATE, id);
        condominium.activate(user.userId());
        repository.flush();
        auditService.record(AuditAction.CONDOMINIUM_ACTIVATED, ENTITY, id, null);
        return mapper.toResponse(condominium);
    }

    /** Carrega o condomínio sempre restrito à organização do usuário (outra organização = 404). */
    private Condominium load(UUID id) {
        UUID organizationId = currentUserProvider.requireOrganizationMember().organizationId();
        return repository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Condomínio"));
    }

    private void apply(Condominium condominium, CondominiumRequest request, String cnpj, UUID userId) {
        condominium.updateDetails(request.name().trim(), CondominiumMapper.trim(request.legalName()), cnpj,
                request.type(), mapper.toAddress(request.address()), CondominiumMapper.trim(request.email()),
                BrazilianDocuments.digitsOnly(request.phone()), userId);
    }

    private static ApiException duplicateCnpj() {
        return ApiException.conflict("CONDOMINIUM_CNPJ_TAKEN", "Já existe um condomínio com este CNPJ na sua administradora.");
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
