package br.com.tekcondo.comum.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UsuarioAtualProvider {

    public static final String CLAIM_ADMINISTRADORA = "org";
    public static final String CLAIM_NOME = "name";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ADMINISTRADOR_PLATAFORMA = "pa";

    public Optional<UsuarioAtual> buscar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        String idAdm = jwt.getClaimAsString(CLAIM_ADMINISTRADORA);
        return Optional.of(new UsuarioAtual(
                UUID.fromString(jwt.getSubject()),
                idAdm == null ? null : UUID.fromString(idAdm),
                jwt.getClaimAsString(CLAIM_EMAIL),
                jwt.getClaimAsString(CLAIM_NOME),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM_ADMINISTRADOR_PLATAFORMA))));
    }

    public UsuarioAtual obter() {
        return buscar().orElseThrow(() -> new AccessDeniedException("Usuário não autenticado"));
    }

    /** Usuário autenticado que pertence a uma organização (exigido por todos os módulos de negócio). */
    public UsuarioAtual exigirMembroAdministradora() {
        UsuarioAtual usuario = obter();
        if (!usuario.pertenceAAdministradora()) {
            throw new AccessDeniedException("Usuário sem organização");
        }
        return usuario;
    }
}
