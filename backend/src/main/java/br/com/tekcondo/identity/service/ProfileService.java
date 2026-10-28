package br.com.tekcondo.identity.service;

import java.util.Map;
import java.util.Set;

import br.com.tekcondo.identity.access.AccessService;
import br.com.tekcondo.identity.access.UserGrants;
import br.com.tekcondo.identity.dto.MeResponse;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.organization.repository.OrganizationRepository;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AccessService accessService;

    public ProfileService(CurrentUserProvider currentUserProvider, UserRepository userRepository,
                          OrganizationRepository organizationRepository, AccessService accessService) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        CurrentUser current = currentUserProvider.get();
        User user = userRepository.findById(current.userId())
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.notFound("Usuário"));
        if (user.getOrganizationId() == null) {
            return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.isPlatformAdmin(), null, null,
                    Set.of(), Map.of());
        }
        String orgName = organizationRepository.findById(user.getOrganizationId()).map(o -> o.getName()).orElse(null);
        UserGrants grants = accessService.currentGrants();
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.isPlatformAdmin(),
                user.getOrganizationId(), orgName, grants.organizationWide(), grants.byCondominium());
    }
}
