package br.com.tekcondo.organization.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.identity.entity.Role;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.entity.UserRole;
import br.com.tekcondo.identity.repository.RefreshTokenRepository;
import br.com.tekcondo.identity.repository.RoleRepository;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.identity.repository.UserRoleRepository;
import br.com.tekcondo.identity.service.PasswordService;
import br.com.tekcondo.organization.dto.OrganizationRequest;
import br.com.tekcondo.organization.dto.OrganizationResponse;
import br.com.tekcondo.organization.dto.OrganizationUser;
import br.com.tekcondo.organization.entity.Organization;
import br.com.tekcondo.organization.repository.OrganizationRepository;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import br.com.tekcondo.shared.validation.BrazilianDocuments;
import br.com.tekcondo.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de administradoras — exclusivo do administrador da plataforma (garantido no SecurityConfig). */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwordService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public OrganizationService(OrganizationRepository organizationRepository, UserRepository userRepository,
                               RoleRepository roleRepository, UserRoleRepository userRoleRepository,
                               RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                               PasswordService passwordService, AuditService auditService,
                               CurrentUserProvider currentUserProvider) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordService = passwordService;
        this.auditService = auditService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> list(Pageable pageable) {
        return PageResponse.of(organizationRepository.findAll(pageable), OrganizationResponse::from);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID id) {
        return OrganizationResponse.from(load(id));
    }

    @Transactional
    public OrganizationResponse create(OrganizationRequest request) {
        CurrentUser actor = currentUserProvider.get();
        String cnpj = BrazilianDocuments.normalizeCnpj(request.cnpj());
        if (organizationRepository.existsByCnpj(cnpj)) {
            throw ApiException.conflict("ORGANIZATION_CNPJ_TAKEN", "Já existe uma organização com este CNPJ.");
        }
        if (userRepository.existsByEmail(request.admin().email())) {
            throw ApiException.conflict("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
        }
        Organization organization = organizationRepository.save(new Organization(request.name().trim(), cnpj,
                trimToNull(request.email()), BrazilianDocuments.digitsOnly(request.phone())));

        User admin = userRepository.save(User.organizationMember(organization.getId(), request.admin().name(),
                request.admin().email(), passwordEncoder.encode(request.admin().password())));
        userRoleRepository.save(new UserRole(admin.getId(), orgAdminRole(), organization.getId(), null, actor.userId()));

        auditService.recordAs(organization.getId(), actor.userId(), actor.email(), AuditAction.ORGANIZATION_CREATED,
                "Organization", organization.getId(), Map.of("name", organization.getName(), "cnpj", cnpj));
        auditService.recordAs(organization.getId(), actor.userId(), actor.email(), AuditAction.USER_CREATED,
                "User", admin.getId(), Map.of("email", admin.getEmail(), "role", Role.ORG_ADMIN));
        return OrganizationResponse.from(organization);
    }

    @Transactional(readOnly = true)
    public List<OrganizationUser> users(UUID organizationId) {
        load(organizationId);
        return userRepository.findAll(inOrganization(organizationId), Sort.by("name")).stream()
                .map(u -> new OrganizationUser(u.getId(), u.getName(), u.getEmail(), u.isActive(), u.hasNeverSignedIn(),
                        u.getLastLoginAt(), userRoleRepository.findByUserIdWithRole(u.getId()).stream()
                        .map(ur -> ur.getRole().getName()).distinct().sorted().toList()))
                .toList();
    }

    /** Convida um administrador para a administradora; o titular define a própria senha pelo link do e-mail. */
    @Transactional
    public OrganizationUser inviteAdmin(UUID organizationId, String name, String email) {
        CurrentUser actor = currentUserProvider.get();
        Organization organization = load(organizationId);
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
        }
        User admin = userRepository.save(User.organizationMember(organization.getId(), name, email,
                passwordService.unusablePasswordHash()));
        Role role = orgAdminRole();
        userRoleRepository.save(new UserRole(admin.getId(), role, organization.getId(), null, actor.userId()));
        auditService.recordAs(organization.getId(), actor.userId(), actor.email(), AuditAction.USER_CREATED,
                "User", admin.getId(), Map.of("email", admin.getEmail(), "role", Role.ORG_ADMIN));
        passwordService.sendInvite(admin);
        return new OrganizationUser(admin.getId(), admin.getName(), admin.getEmail(), true, true, null,
                List.of(role.getName()));
    }

    /** Inativar bloqueia o acesso de todos os usuários da administradora; os dados são preservados. */
    @Transactional
    public OrganizationResponse setActive(UUID organizationId, boolean active) {
        CurrentUser actor = currentUserProvider.get();
        Organization organization = load(organizationId);
        if (organization.isActive() == active) {
            throw ApiException.businessRule("ORGANIZATION_STATUS_UNCHANGED",
                    active ? "A administradora já está ativa." : "A administradora já está inativa.");
        }
        organization.setActive(active);
        if (!active) {
            Instant now = Instant.now();
            userRepository.findAll(inOrganization(organizationId))
                    .forEach(u -> refreshTokenRepository.revokeAllForUser(u.getId(), now));
        }
        organizationRepository.flush();
        auditService.recordAs(organizationId, actor.userId(), actor.email(),
                active ? AuditAction.ORGANIZATION_ACTIVATED : AuditAction.ORGANIZATION_DEACTIVATED,
                "Organization", organizationId, null);
        return OrganizationResponse.from(organization);
    }

    private Organization load(UUID id) {
        return organizationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Organização"));
    }

    private Role orgAdminRole() {
        return roleRepository.findSystemRole(Role.ORG_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Papel ORG_ADMIN ausente — verifique as migrações"));
    }

    private static Specification<User> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
