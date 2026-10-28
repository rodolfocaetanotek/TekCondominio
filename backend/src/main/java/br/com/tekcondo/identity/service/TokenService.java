package br.com.tekcondo.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import br.com.tekcondo.identity.entity.RefreshToken;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.repository.RefreshTokenRepository;
import br.com.tekcondo.shared.config.TekCondoProperties;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emissão de access tokens (JWT) e refresh tokens opacos. */
@Service
public class TokenService {

    public static final String ISSUER = "tekcondo";

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TekCondoProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokenRepository, TekCondoProperties props,
                        Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.props = props;
        this.clock = clock;
    }

    public record IssuedTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {
    }

    /** Deve ser chamado dentro de uma transação (persiste o refresh token). */
    public IssuedTokens issue(User user) {
        Instant now = Instant.now(clock);
        Instant accessExpiry = now.plus(props.security().accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(accessExpiry)
                .claim(CurrentUserProvider.CLAIM_NAME, user.getName())
                .claim(CurrentUserProvider.CLAIM_EMAIL, user.getEmail())
                .claim(CurrentUserProvider.CLAIM_PLATFORM_ADMIN, user.isPlatformAdmin());
        if (user.getOrganizationId() != null) {
            claims.claim(CurrentUserProvider.CLAIM_ORGANIZATION, user.getOrganizationId().toString());
        }
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        refreshTokenRepository.save(new RefreshToken(user.getId(), hash(refreshToken), now,
                now.plus(props.security().refreshTokenTtl())));
        return new IssuedTokens(accessToken, accessExpiry, refreshToken);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
