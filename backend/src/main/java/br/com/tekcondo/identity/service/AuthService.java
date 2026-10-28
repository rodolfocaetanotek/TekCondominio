package br.com.tekcondo.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.identity.entity.RefreshToken;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.repository.RefreshTokenRepository;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.organization.repository.OrganizationRepository;
import br.com.tekcondo.shared.config.TekCondoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ENTITY = "User";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrganizationRepository organizationRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final TekCondoProperties props;
    private final Clock clock;
    /** Hash usado quando o e-mail não existe, para que o tempo de resposta não revele contas cadastradas. */
    private final String timingDummyHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       OrganizationRepository organizationRepository, TokenService tokenService,
                       PasswordEncoder passwordEncoder, AuditService auditService, TekCondoProperties props, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.organizationRepository = organizationRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.props = props;
        this.clock = clock;
        this.timingDummyHash = passwordEncoder.encode("timing-equalization-placeholder");
    }

    public record AuthResult(User user, TokenService.IssuedTokens tokens) {
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthResult login(String email, String password) {
        Instant now = Instant.now(clock);
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, timingDummyHash);
            auditService.recordAs(null, null, email.trim().toLowerCase(), AuditAction.LOGIN_FAILED, ENTITY, null,
                    Map.of("reason", "UNKNOWN_EMAIL"));
            throw AuthenticationFailedException.invalidCredentials();
        }
        if (user.isLocked(now)) {
            audit(user, AuditAction.LOGIN_BLOCKED, Map.of("lockedUntil", user.getLockedUntil().toString()));
            throw AuthenticationFailedException.locked();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean locked = user.registerFailedLogin(props.security().maxFailedLogins(),
                    props.security().lockoutDuration(), now);
            audit(user, AuditAction.LOGIN_FAILED, Map.of("reason", "BAD_PASSWORD"));
            if (locked) {
                audit(user, AuditAction.ACCOUNT_LOCKED, Map.of("lockedUntil", user.getLockedUntil().toString()));
                log.warn("Conta {} bloqueada por excesso de tentativas", user.getId());
                throw AuthenticationFailedException.locked();
            }
            throw AuthenticationFailedException.invalidCredentials();
        }
        if (!isAllowedToSignIn(user)) {
            audit(user, AuditAction.LOGIN_FAILED, Map.of("reason", "INACTIVE"));
            throw AuthenticationFailedException.invalidCredentials();
        }
        user.registerSuccessfulLogin(now);
        audit(user, AuditAction.LOGIN_SUCCESS, null);
        return new AuthResult(user, tokenService.issue(user));
    }

    /**
     * Rotação de refresh token. Reapresentar um token já revogado indica possível roubo: todas as sessões
     * do usuário são revogadas.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenService.hash(rawRefreshToken))
                .orElseThrow(AuthenticationFailedException::sessionExpired);
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(AuthenticationFailedException::sessionExpired);
        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
            audit(user, AuditAction.REFRESH_TOKEN_REUSE, null);
            log.warn("Reutilização de refresh token revogado para o usuário {}; sessões revogadas", user.getId());
            throw AuthenticationFailedException.sessionExpired();
        }
        if (stored.isExpired(now) || !isAllowedToSignIn(user)) {
            stored.revoke(now);
            throw AuthenticationFailedException.sessionExpired();
        }
        stored.revoke(now);
        return new AuthResult(user, tokenService.issue(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenService.hash(rawRefreshToken)).ifPresent(token -> {
            token.revoke(Instant.now(clock));
            userRepository.findById(token.getUserId()).ifPresent(user -> audit(user, AuditAction.LOGOUT, null));
        });
    }

    private boolean isAllowedToSignIn(User user) {
        if (!user.isActive()) {
            return false;
        }
        return user.isPlatformAdmin() && user.getOrganizationId() == null
                || organizationRepository.findById(user.getOrganizationId()).map(o -> o.isActive()).orElse(false);
    }

    private void audit(User user, AuditAction action, Map<String, Object> details) {
        auditService.recordAs(user.getOrganizationId(), user.getId(), user.getEmail(), action, ENTITY, user.getId(),
                details);
    }
}
