package br.com.tekcondo.acesso.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.acesso.entity.RefreshToken;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.repository.RefreshTokenRepository;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.administradora.repository.AdministradoraRepository;
import br.com.tekcondo.comum.config.TekCondoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ENTIDADE = "User";

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdministradoraRepository administradoraRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final TekCondoProperties props;
    private final Clock clock;
    /** Hash usado quando o e-mail não existe, para que o tempo de resposta não revele contas cadastradas. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarioRepository, RefreshTokenRepository refreshTokenRepository,
                       AdministradoraRepository administradoraRepository, TokenService tokenService,
                       PasswordEncoder passwordEncoder, AuditoriaService auditoriaService, TekCondoProperties props, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.administradoraRepository = administradoraRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.props = props;
        this.clock = clock;
        this.hashFicticio = passwordEncoder.encode("timing-equalization-placeholder");
    }

    public record ResultadoAutenticacao(Usuario usuario, TokenService.TokensEmitidos tokens) {
    }

    @Transactional(noRollbackFor = FalhaAutenticacaoException.class)
    public ResultadoAutenticacao login(String email, String senha) {
        Instant agora = Instant.now(clock);
        Usuario usuario = usuarioRepository.buscarPorEmail(email.trim()).orElse(null);
        if (usuario == null) {
            passwordEncoder.matches(senha, hashFicticio);
            auditoriaService.registrarComo(null, null, email.trim().toLowerCase(), AcaoAuditoria.LOGIN_FAILED, ENTIDADE, null,
                    Map.of("reason", "UNKNOWN_EMAIL"));
            throw FalhaAutenticacaoException.credenciaisInvalidas();
        }
        if (usuario.isBloqueado(agora)) {
            auditar(usuario, AcaoAuditoria.LOGIN_BLOCKED, Map.of("lockedUntil", usuario.getBloqueadoAte().toString()));
            throw FalhaAutenticacaoException.contaBloqueada();
        }
        if (!passwordEncoder.matches(senha, usuario.getHashSenha())) {
            boolean bloqueado = usuario.registrarFalhaLogin(props.security().maxFailedLogins(),
                    props.security().lockoutDuration(), agora);
            auditar(usuario, AcaoAuditoria.LOGIN_FAILED, Map.of("reason", "BAD_PASSWORD"));
            if (bloqueado) {
                auditar(usuario, AcaoAuditoria.ACCOUNT_LOCKED, Map.of("lockedUntil", usuario.getBloqueadoAte().toString()));
                log.warn("Conta {} bloqueada por excesso de tentativas", usuario.getId());
                throw FalhaAutenticacaoException.contaBloqueada();
            }
            throw FalhaAutenticacaoException.credenciaisInvalidas();
        }
        if (!podeEntrar(usuario)) {
            auditar(usuario, AcaoAuditoria.LOGIN_FAILED, Map.of("reason", "INACTIVE"));
            throw FalhaAutenticacaoException.credenciaisInvalidas();
        }
        usuario.registrarLoginSucesso(agora);
        auditar(usuario, AcaoAuditoria.LOGIN_SUCCESS, null);
        return new ResultadoAutenticacao(usuario, tokenService.emitir(usuario));
    }

    /**
     * Rotação de refresh token. Reapresentar um token já revogado indica possível roubo: todas as sessões
     * do usuário são revogadas.
     */
    @Transactional(noRollbackFor = FalhaAutenticacaoException.class)
    public ResultadoAutenticacao refresh(String refreshTokenBruto) {
        Instant agora = Instant.now(clock);
        RefreshToken armazenado = refreshTokenRepository.findByTokenHash(TokenService.hash(refreshTokenBruto))
                .orElseThrow(FalhaAutenticacaoException::sessaoExpirada);
        Usuario usuario = usuarioRepository.findById(armazenado.getIdUsuario())
                .orElseThrow(FalhaAutenticacaoException::sessaoExpirada);
        if (armazenado.isRevogado()) {
            refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), agora);
            auditar(usuario, AcaoAuditoria.REFRESH_TOKEN_REUSE, null);
            log.warn("Reutilização de refresh token revogado para o usuário {}; sessões revogadas", usuario.getId());
            throw FalhaAutenticacaoException.sessaoExpirada();
        }
        if (armazenado.isExpirado(agora) || !podeEntrar(usuario)) {
            armazenado.revogar(agora);
            throw FalhaAutenticacaoException.sessaoExpirada();
        }
        armazenado.revogar(agora);
        return new ResultadoAutenticacao(usuario, tokenService.emitir(usuario));
    }

    @Transactional
    public void logout(String refreshTokenBruto) {
        if (refreshTokenBruto == null || refreshTokenBruto.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenService.hash(refreshTokenBruto)).ifPresent(token -> {
            token.revogar(Instant.now(clock));
            usuarioRepository.findById(token.getIdUsuario()).ifPresent(usuario -> auditar(usuario, AcaoAuditoria.LOGOUT, null));
        });
    }

    private boolean podeEntrar(Usuario usuario) {
        if (!usuario.isAtivo()) {
            return false;
        }
        return usuario.isAdministradorPlataforma() && usuario.getIdAdministradora() == null
                || administradoraRepository.findById(usuario.getIdAdministradora()).map(o -> o.isAtivo()).orElse(false);
    }

    private void auditar(Usuario usuario, AcaoAuditoria acao, Map<String, Object> detalhes) {
        auditoriaService.registrarComo(usuario.getIdAdministradora(), usuario.getId(), usuario.getEmail(), acao, ENTIDADE, usuario.getId(),
                detalhes);
    }
}
