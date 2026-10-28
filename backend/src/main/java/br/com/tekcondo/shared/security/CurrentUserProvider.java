package br.com.tekcondo.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public static final String CLAIM_ORGANIZATION = "org";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_PLATFORM_ADMIN = "pa";

    public Optional<CurrentUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        String org = jwt.getClaimAsString(CLAIM_ORGANIZATION);
        return Optional.of(new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                org == null ? null : UUID.fromString(org),
                jwt.getClaimAsString(CLAIM_EMAIL),
                jwt.getClaimAsString(CLAIM_NAME),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM_PLATFORM_ADMIN))));
    }

    public CurrentUser get() {
        return find().orElseThrow(() -> new AccessDeniedException("Usuário não autenticado"));
    }

    /** Usuário autenticado que pertence a uma organização (exigido por todos os módulos de negócio). */
    public CurrentUser requireOrganizationMember() {
        CurrentUser user = get();
        if (!user.belongsToOrganization()) {
            throw new AccessDeniedException("Usuário sem organização");
        }
        return user;
    }
}
