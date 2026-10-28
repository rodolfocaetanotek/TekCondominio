package br.com.tekcondo.acesso.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import br.com.tekcondo.acesso.entity.RefreshToken;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.repository.RefreshTokenRepository;
import br.com.tekcondo.comum.config.TekCondoProperties;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
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

    public record TokensEmitidos(String accessToken, Instant accessTokenExpiraEm, String refreshToken) {
    }

    /** Deve ser chamado dentro de uma transação (persiste o refresh token). */
    public TokensEmitidos emitir(Usuario usuario) {
        Instant agora = Instant.now(clock);
        Instant expiracaoAccess = agora.plus(props.security().accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(expiracaoAccess)
                .claim(UsuarioAtualProvider.CLAIM_NOME, usuario.getNome())
                .claim(UsuarioAtualProvider.CLAIM_EMAIL, usuario.getEmail())
                .claim(UsuarioAtualProvider.CLAIM_ADMINISTRADOR_PLATAFORMA, usuario.isAdministradorPlataforma());
        if (usuario.getIdAdministradora() != null) {
            claims.claim(UsuarioAtualProvider.CLAIM_ADMINISTRADORA, usuario.getIdAdministradora().toString());
        }
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();

        byte[] bruto = new byte[32];
        random.nextBytes(bruto);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
        refreshTokenRepository.save(new RefreshToken(usuario.getId(), hash(refreshToken), agora,
                agora.plus(props.security().refreshTokenTtl())));
        return new TokensEmitidos(accessToken, expiracaoAccess, refreshToken);
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
