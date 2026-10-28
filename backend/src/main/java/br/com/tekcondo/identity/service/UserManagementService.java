package br.com.tekcondo.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
import br.com.tekcondo.identity.access.AccessService;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.identity.dto.UserDtos;
import br.com.tekcondo.identity.entity.Role;
import br.com.tekcondo.identity.entity.RoleScope;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.entity.UserRole;
import br.com.tekcondo.identity.repository.RefreshTokenRepository;
import br.com.tekcondo.identity.repository.RoleRepository;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.identity.repository.UserRoleRepository;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import br.com.tekcondo.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de usuários da administradora: cadastro por convite, ativação, papéis por organização ou condomínio.
 * Exige USER_VIEW / USER_MANAGE válidos para toda a organização. Garante que a administradora nunca fique
 * sem ao menos um administrador ativo.
 */
@Service
public class UserManagementService {

    private static final String ENTITY = "User";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CondominiumRepository condominiumRepository;
    private final PasswordService passwordService;
    private final AccessService accessService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    public UserManagementService(UserRepository userRepository, RoleRepository roleRepository,
                                 UserRoleRepository userRoleRepository, RefreshTokenRepository refreshTokenRepository,
                                 CondominiumRepository condominiumRepository, PasswordService passwordService,
                                 AccessService accessService, CurrentUserProvider currentUserProvider,
                                 AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.condominiumRepository = condominiumRepository;
        this.passwordService = passwordService;
        this.accessService = accessService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.Summary> list(String search, Boolean active, Pageable pageable) {
        UUID orgId = currentUserProvider.requireOrganizationMember().organizationId();
        accessService.requireOrganizationWide(Permission.USER_VIEW);
        Specification<User> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("organizationId"), orgId);
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                p = cb.and(p, cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')));
            }
            if (active != null) {
                p = cb.and(p, cb.equal(root.get("active"), active));
            }
            return p;
        };
        Page<User> page = userRepository.findAll(spec, pageable);
        Map<UUID, List<String>> roles = userRoleRepository
                .findByUserIdsWithRole(page.getContent().stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(ur -> ur.getRole().getName(), Collectors.toList())));
        return PageResponse.of(page, u -> new UserDtos.Summary(u.getId(), u.getName(), u.getEmail(), u.isActive(),
                u.hasNeverSignedIn(), u.getLastLoginAt(), roles.getOrDefault(u.getId(), List.of()).stream().distinct().sorted().toList()));
    }

    @Transactional(readOnly = true)
    public UserDtos.Detail get(UUID id) {
        accessService.requireOrganizationWide(Permission.USER_VIEW);
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public List<UserDtos.RoleResponse> roles() {
        UUID orgId = currentUserProvider.requireOrganizationMember().organizationId();
        accessService.requireOrganizationWide(Permission.USER_VIEW);
        return roleRepository.findAvailableTo(orgId).stream()
                .map(r -> new UserDtos.RoleResponse(r.getCode(), r.getName(), r.getDescription(), r.getScope(), r.getPermissions()))
                .toList();
    }

    @Transactional
    public UserDtos.Detail create(UserDtos.CreateRequest request) {
        CurrentUser actor = currentUserProvider.requireOrganizationMember();
        accessService.requireOrganizationWide(Permission.USER_MANAGE);
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
        }
        // Valida o papel antes de criar, para não deixar usuário sem acesso em caso de erro.
        Role role = resolveRole(request.assignment().roleCode());
        validateScope(role, request.assignment().condominiumId(), actor.organizationId());

        User user = userRepository.save(User.organizationMember(actor.organizationId(), request.name(), request.email(),
                passwordService.unusablePasswordHash()));
        auditService.record(AuditAction.USER_CREATED, ENTITY, user.getId(),
                Map.of("name", user.getName(), "email", user.getEmail()));
        assign(user, role, request.assignment().condominiumId(), actor);
        passwordService.sendInvite(user);
        return toDetail(user);
    }

    @Transactional
    public UserDtos.Detail update(UUID id, UserDtos.UpdateRequest request) {
        accessService.requireOrganizationWide(Permission.USER_MANAGE);
        User user = load(id);
        if (!request.version().equals(user.getVersion())) {
            throw ApiException.staleVersion();
        }
        String before = user.getName();
        user.rename(request.name());
        userRepository.flush();
        if (!before.equals(user.getName())) {
            auditService.record(AuditAction.USER_UPDATED, ENTITY, id,
                    Map.of("name", Map.of("from", before, "to", user.getName())));
        }
        return toDetail(user);
    }

    @Transactional
    public UserDtos.Detail deactivate(UUID id) {
        CurrentUser actor = currentUserProvider.requireOrganizationMember();
        accessService.requireOrganizationWide(Permission.USER_MANAGE);
        User user = load(id);
        if (user.getId().equals(actor.userId())) {
            throw ApiException.businessRule("CANNOT_DEACTIVATE_SELF", "Você não pode desativar o próprio usuário.");
        }
        if (!user.isActive()) {
            throw ApiException.businessRule("USER_ALREADY_INACTIVE", "O usuário já está inativo.");
        }
        ensureNotLastAdmin(user, userRoleRepository.findByUserIdWithRole(user.getId()));
        user.deactivate();
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now(clock));
        userRepository.flush();
        auditService.record(AuditAction.USER_DEACTIVATED, ENTITY, id, null);
        return toDetail(user);
    }

    @Transactional
    public UserDtos.Detail activate(UUID id) {
        accessService.requireOrganizationWide(Permission.USER_MANAGE);
        User user = load(id);
        if (user.isActive()) {
            throw ApiException.businessRule("USER_ALREADY_ACTIVE", "O usuário já está ativo.");
        }
        user.activate();
        userRepository.flush();
        auditService.record(AuditAction.USER_ACTIVATED, ENTITY, id, null);
        return toDetail(user);
    }

    @Transactional
    public UserDtos.Detail addAssignment(UUID id, UserDtos.AssignmentRequest request) {
        CurrentUser actor = currentUserProvider.requireOrganizationMember();
        accessService.requireOrganizationWide(Permission.USER_MANAGE);
        User user = load(id);
        Role role = resolveRole(request.roleCode());
        validateScope(role, request.condominiumId(), actor.organizationId());
        assign(user, role, request.condominiumId(), actor);
        return toDetail(user);
    }

    @Transactional
    public UserDtos.Detail removeAssignment(UUID id, UUID assignmentId) {
        accessService.requireOrganizationWide(Permission.USER_MANAGE);
        User user = load(id);
        List<UserRole> current = userRoleRepository.findByUserIdWithRole(user.getId());
        UserRole target = current.stream().filter(ur -> ur.getId().equals(assignmentId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Atribuição de papel"));
        if (isOrganizationAdmin(target) && user.isActive()
                && userRoleRepository.countActiveOrganizationAdmins(user.getOrganizationId()) <= 1) {
            throw lastAdmin();
        }
        userRoleRepository.delete(target);
        auditService.record(AuditAction.ROLE_REVOKED, ENTITY, id, assignmentDetails(target.getRole(), target.getCondominiumId()));
        userRoleRepository.flush();
        return toDetail(user);
    }

    @Transactional
    public void resendInvite(UUID id) {
        accessService.requireOrganizationWide(Permission.USER_MANAGE);
        User user = load(id);
        if (!user.isActive() || !user.hasNeverSignedIn()) {
            throw ApiException.businessRule("INVITE_NOT_APPLICABLE",
                    "O convite só pode ser reenviado para usuários ativos que ainda não acessaram o sistema.");
        }
        passwordService.sendInvite(user);
    }

    // ------------------------------------------------------------------

    private User load(UUID id) {
        UUID orgId = currentUserProvider.requireOrganizationMember().organizationId();
        return userRepository.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> ApiException.notFound("Usuário"));
    }

    private Role resolveRole(String code) {
        return roleRepository.findSystemRole(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.businessRule("ROLE_NOT_FOUND", "Papel inexistente: " + code));
    }

    private void validateScope(Role role, UUID condominiumId, UUID orgId) {
        if (role.getScope() == RoleScope.ORGANIZATION && condominiumId != null) {
            throw ApiException.businessRule("ROLE_SCOPE_MISMATCH",
                    "O papel \"" + role.getName() + "\" vale para toda a administradora e não deve ter condomínio.");
        }
        if (role.getScope() == RoleScope.CONDOMINIUM) {
            if (condominiumId == null) {
                throw ApiException.businessRule("ROLE_SCOPE_MISMATCH",
                        "Informe o condomínio para o papel \"" + role.getName() + "\".");
            }
            condominiumRepository.findByIdAndOrganizationId(condominiumId, orgId)
                    .orElseThrow(() -> ApiException.notFound("Condomínio"));
        }
    }

    private void assign(User user, Role role, UUID condominiumId, CurrentUser actor) {
        boolean exists = condominiumId == null
                ? userRoleRepository.existsOrganizationWide(user.getId(), role.getId())
                : userRoleRepository.existsInCondominium(user.getId(), role.getId(), condominiumId);
        if (exists) {
            throw ApiException.conflict("ROLE_ALREADY_ASSIGNED", "O usuário já possui este papel neste escopo.");
        }
        userRoleRepository.save(new UserRole(user.getId(), role, actor.organizationId(), condominiumId, actor.userId()));
        auditService.record(AuditAction.ROLE_ASSIGNED, ENTITY, user.getId(), assignmentDetails(role, condominiumId));
    }

    private void ensureNotLastAdmin(User user, List<UserRole> assignments) {
        boolean isAdmin = assignments.stream().anyMatch(this::isOrganizationAdmin);
        if (isAdmin && userRoleRepository.countActiveOrganizationAdmins(user.getOrganizationId()) <= 1) {
            throw lastAdmin();
        }
    }

    private boolean isOrganizationAdmin(UserRole ur) {
        return ur.getCondominiumId() == null && Role.ORG_ADMIN.equals(ur.getRole().getCode());
    }

    private static ApiException lastAdmin() {
        return ApiException.businessRule("LAST_ADMIN",
                "A administradora precisa de ao menos um administrador ativo. Designe outro administrador antes.");
    }

    private static Map<String, Object> assignmentDetails(Role role, UUID condominiumId) {
        return condominiumId == null ? Map.of("role", role.getCode())
                : Map.of("role", role.getCode(), "condominiumId", condominiumId.toString());
    }

    private UserDtos.Detail toDetail(User user) {
        List<UserRole> assignments = userRoleRepository.findByUserIdWithRole(user.getId());
        Map<UUID, String> condoNames = condominiumNames(assignments.stream()
                .map(UserRole::getCondominiumId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()),
                user.getOrganizationId());
        List<UserDtos.Assignment> items = assignments.stream()
                .map(ur -> new UserDtos.Assignment(ur.getId(), ur.getRole().getCode(), ur.getRole().getName(),
                        ur.getRole().getScope(), ur.getCondominiumId(),
                        ur.getCondominiumId() == null ? null : condoNames.get(ur.getCondominiumId())))
                .sorted(Comparator.comparing((UserDtos.Assignment a) -> a.scope().ordinal())
                        .thenComparing(a -> a.condominiumName() == null ? "" : a.condominiumName())
                        .thenComparing(UserDtos.Assignment::roleName))
                .toList();
        return new UserDtos.Detail(user.getId(), user.getName(), user.getEmail(), user.isActive(),
                user.hasNeverSignedIn(), user.getLastLoginAt(), user.getCreatedAt(), user.getVersion(), items);
    }

    private Map<UUID, String> condominiumNames(Collection<UUID> ids, UUID orgId) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> wanted = new HashSet<>(ids);
        return condominiumRepository.findAllById(wanted).stream()
                .filter(c -> c.getOrganizationId().equals(orgId))
                .collect(Collectors.toMap(Condominium::getId, Condominium::getName, (a, b) -> a));
    }

}
