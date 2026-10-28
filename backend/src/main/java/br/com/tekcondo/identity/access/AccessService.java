package br.com.tekcondo.identity.access;

import java.util.UUID;

import br.com.tekcondo.identity.repository.UserRoleRepository;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Ponto único de autorização de negócio. As permissões são lidas do banco (não do token), garantindo que
 * revogações e inativações tenham efeito imediato; o resultado é memorizado durante a requisição.
 */
@Service
public class AccessService {

    private static final String GRANTS_ATTRIBUTE = AccessService.class.getName() + ".grants";

    private final UserRoleRepository userRoleRepository;
    private final CurrentUserProvider currentUserProvider;

    public AccessService(UserRoleRepository userRoleRepository, CurrentUserProvider currentUserProvider) {
        this.userRoleRepository = userRoleRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public UserGrants currentGrants() {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getAttribute(GRANTS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof UserGrants cached) {
            return cached;
        }
        UserGrants grants = new UserGrants(userRoleRepository.findGrants(user.userId(), user.organizationId()));
        if (attrs != null) {
            attrs.setAttribute(GRANTS_ATTRIBUTE, grants, RequestAttributes.SCOPE_REQUEST);
        }
        return grants;
    }

    /** Exige a permissão no condomínio informado (ou na organização inteira). */
    public void require(Permission permission, UUID condominiumId) {
        if (!currentGrants().has(permission, condominiumId)) {
            throw new AccessDeniedException("Permissão negada: " + permission);
        }
    }

    /** Exige a permissão válida para toda a organização (ex.: cadastrar novo condomínio). */
    public void requireOrganizationWide(Permission permission) {
        if (!currentGrants().hasOrganizationWide(permission)) {
            throw new AccessDeniedException("Permissão negada: " + permission);
        }
    }

    /** Escopo para listagens; lança 403 se o usuário não tiver a permissão em nenhum condomínio. */
    public AccessScope requireScope(Permission permission) {
        AccessScope scope = currentGrants().scope(permission);
        if (scope.isEmpty()) {
            throw new AccessDeniedException("Permissão negada: " + permission);
        }
        return scope;
    }
}
