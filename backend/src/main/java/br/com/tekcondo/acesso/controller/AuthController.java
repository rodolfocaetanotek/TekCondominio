package br.com.tekcondo.acesso.controller;

import java.time.Duration;

import br.com.tekcondo.acesso.dto.LoginRequest;
import br.com.tekcondo.acesso.dto.PerfilResponse;
import br.com.tekcondo.acesso.dto.TokenResponse;
import br.com.tekcondo.acesso.service.AuthService;
import br.com.tekcondo.acesso.service.PerfilService;
import br.com.tekcondo.comum.config.TekCondoProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
public class AuthController {

    public static final String REFRESH_COOKIE = "tekcondo_refresh";
    /** Cabeçalho obrigatório nos endpoints por cookie: força preflight CORS e bloqueia CSRF. */
    public static final String CLIENT_HEADER = "X-TekCondo-Client";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final PerfilService perfilService;
    private final TekCondoProperties props;

    public AuthController(AuthService authService, PerfilService perfilService, TekCondoProperties props) {
        this.authService = authService;
        this.perfilService = perfilService;
        this.props = props;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica com e-mail e senha", description = "Retorna o access token e grava o refresh token em cookie HttpOnly.")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.ResultadoAutenticacao resultado = authService.login(request.email(), request.senha());
        gravarCookieRefresh(response, resultado.tokens().refreshToken(), props.security().refreshTokenTtl());
        return TokenResponse.bearer(resultado.tokens().accessToken(), resultado.tokens().accessTokenExpiraEm());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o access token usando o cookie de refresh (rotativo)")
    public TokenResponse refresh(@RequestHeader(CLIENT_HEADER) String cliente,
                                 @CookieValue(REFRESH_COOKIE) String refreshToken,
                                 HttpServletResponse response) {
        AuthService.ResultadoAutenticacao resultado = authService.refresh(refreshToken);
        gravarCookieRefresh(response, resultado.tokens().refreshToken(), props.security().refreshTokenTtl());
        return TokenResponse.bearer(resultado.tokens().accessToken(), resultado.tokens().accessTokenExpiraEm());
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão (revoga o refresh token)")
    public ResponseEntity<Void> logout(@RequestHeader(CLIENT_HEADER) String cliente,
                                       @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                       HttpServletResponse response) {
        authService.logout(refreshToken);
        gravarCookieRefresh(response, "", Duration.ZERO);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/perfil")
    @Operation(summary = "Perfil e permissões do usuário autenticado")
    public PerfilResponse perfil() {
        return perfilService.perfil();
    }

    private void gravarCookieRefresh(HttpServletResponse response, String valor, Duration validade) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, valor)
                .httpOnly(true)
                .secure(props.security().refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(validade)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
