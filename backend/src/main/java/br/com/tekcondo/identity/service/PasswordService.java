package br.com.tekcondo.identity.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.identity.dto.PasswordTokenInfo;
import br.com.tekcondo.identity.entity.PasswordToken;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.repository.PasswordTokenRepository;
import br.com.tekcondo.identity.repository.RefreshTokenRepository;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.integration.mail.MailDispatcher;
import br.com.tekcondo.organization.repository.OrganizationRepository;
import br.com.tekcondo.shared.config.TekCondoProperties;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida da senha: convite, esqueci a senha, redefinição por link e troca pelo próprio usuário. */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    private static final String ENTITY = "User";

    private final UserRepository userRepository;
    private final PasswordTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailDispatcher mail;
    private final AccountEmails emails;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final TekCondoProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(UserRepository userRepository, PasswordTokenRepository tokenRepository,
                           RefreshTokenRepository refreshTokenRepository, OrganizationRepository organizationRepository,
                           PasswordEncoder passwordEncoder, MailDispatcher mail, AccountEmails emails,
                           AuditService auditService, CurrentUserProvider currentUserProvider,
                           TekCondoProperties props, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.emails = emails;
        this.auditService = auditService;
        this.currentUserProvider = currentUserProvider;
        this.props = props;
        this.clock = clock;
    }

    /** Hash de uma senha aleatória descartada: conta criada por convite não tem senha utilizável. */
    public String unusablePasswordHash() {
        return passwordEncoder.encode(randomToken());
    }

    /** Emite convite (deve ser chamado dentro da transação que criou/alterou o usuário). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void sendInvite(User user) {
        String token = issue(user, PasswordToken.Purpose.INVITE, props.security().inviteTtl());
        String orgName = user.getOrganizationId() == null ? null
                : organizationRepository.findById(user.getOrganizationId()).map(o -> o.getName()).orElse(null);
        mail.sendAfterCommit(emails.invite(user, orgName, token));
        CurrentUser actor = currentUserProvider.get();
        auditService.recordAs(user.getOrganizationId(), actor.userId(), actor.email(), AuditAction.INVITE_SENT, ENTITY,
                user.getId(), Map.of("email", user.getEmail()));
    }

    /**
     * Esqueci a senha. Resposta sempre igual (não revela se o e-mail existe). Limite de solicitações por hora
     * para evitar abuso da caixa de entrada do usuário.
     */
    @Transactional
    public void requestReset(String email) {
        Instant now = Instant.now(clock);
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null || !canSignIn(user)) {
            return;
        }
        long recent = tokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), PasswordToken.Purpose.RESET,
                now.minus(Duration.ofHours(1)));
        if (recent >= props.security().maxResetRequestsPerHour()) {
            log.warn("Limite de pedidos de redefinição atingido para o usuário {}", user.getId());
            return;
        }
        String token = issue(user, PasswordToken.Purpose.RESET, props.security().passwordResetTtl());
        mail.sendAfterCommit(emails.passwordReset(user, token));
        auditService.recordAs(user.getOrganizationId(), user.getId(), user.getEmail(),
                AuditAction.PASSWORD_RESET_REQUESTED, ENTITY, user.getId(), null);
    }

    /** Informações para a tela de definição de senha (sem consumir o token). */
    @Transactional(readOnly = true)
    public PasswordTokenInfo describe(String rawToken) {
        PasswordToken token = usableToken(rawToken);
        User user = userRepository.findById(token.getUserId()).filter(this::canSignIn).orElseThrow(PasswordService::invalidLink);
        return new PasswordTokenInfo(token.getPurpose(), user.getName(), user.getEmail());
    }

    /** Define a nova senha a partir do link. Encerra todas as sessões abertas do usuário. */
    @Transactional
    public void complete(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordToken token = usableToken(rawToken);
        User user = userRepository.findById(token.getUserId()).filter(this::canSignIn).orElseThrow(PasswordService::invalidLink);
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        tokenRepository.invalidatePending(user.getId(), now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        AuditAction action = token.getPurpose() == PasswordToken.Purpose.INVITE
                ? AuditAction.INVITE_ACCEPTED : AuditAction.PASSWORD_RESET_COMPLETED;
        auditService.recordAs(user.getOrganizationId(), user.getId(), user.getEmail(), action, ENTITY, user.getId(), null);
    }

    /** Troca de senha pelo próprio usuário; mantém a sessão atual e encerra as demais. */
    @Transactional
    public void change(String currentPassword, String newPassword, String currentRefreshToken) {
        CurrentUser current = currentUserProvider.get();
        User user = userRepository.findById(current.userId()).orElseThrow(() -> ApiException.notFound("Usuário"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "WRONG_CURRENT_PASSWORD", "A senha atual não confere.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.businessRule("SAME_PASSWORD", "A nova senha deve ser diferente da atual.");
        }
        Instant now = Instant.now(clock);
        user.changePassword(passwordEncoder.encode(newPassword));
        if (currentRefreshToken != null && !currentRefreshToken.isBlank()) {
            refreshTokenRepository.revokeAllForUserExcept(user.getId(), TokenService.hash(currentRefreshToken), now);
        } else {
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
        }
        tokenRepository.invalidatePending(user.getId(), now);
        auditService.recordAs(user.getOrganizationId(), user.getId(), user.getEmail(), AuditAction.PASSWORD_CHANGED,
                ENTITY, user.getId(), null);
    }

    private String issue(User user, PasswordToken.Purpose purpose, Duration ttl) {
        Instant now = Instant.now(clock);
        tokenRepository.invalidatePending(user.getId(), now);
        String raw = randomToken();
        tokenRepository.save(new PasswordToken(user.getId(), purpose, TokenService.hash(raw), now, now.plus(ttl)));
        return raw;
    }

    private PasswordToken usableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            throw invalidLink();
        }
        return tokenRepository.findByTokenHash(TokenService.hash(rawToken.trim()))
                .filter(t -> t.isUsable(Instant.now(clock)))
                .orElseThrow(PasswordService::invalidLink);
    }

    private boolean canSignIn(User user) {
        if (!user.isActive()) {
            return false;
        }
        return user.getOrganizationId() == null
                || organizationRepository.findById(user.getOrganizationId()).map(o -> o.isActive()).orElse(false);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ApiException invalidLink() {
        return new ApiException(HttpStatus.GONE, "INVALID_LINK",
                "Este link é inválido, já foi utilizado ou expirou. Solicite um novo.");
    }
}
