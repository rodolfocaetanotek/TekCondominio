package br.com.tekcondo.acesso.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.acesso.dto.TokenSenhaInfo;
import br.com.tekcondo.acesso.entity.TokenSenha;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.repository.TokenSenhaRepository;
import br.com.tekcondo.acesso.repository.RefreshTokenRepository;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.integracao.email.EmailDispatcher;
import br.com.tekcondo.administradora.repository.AdministradoraRepository;
import br.com.tekcondo.comum.config.TekCondoProperties;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida da senha: convite, esqueci a senha, redefinição por link e troca pelo próprio usuário. */
@Service
public class SenhaService {

    private static final Logger log = LoggerFactory.getLogger(SenhaService.class);
    private static final String ENTIDADE = "User";

    private final UsuarioRepository usuarioRepository;
    private final TokenSenhaRepository tokenSenhaRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdministradoraRepository administradoraRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailDispatcher emailDispatcher;
    private final EmailsConta emailsConta;
    private final AuditoriaService auditoriaService;
    private final UsuarioAtualProvider usuarioAtualProvider;
    private final TekCondoProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SenhaService(UsuarioRepository usuarioRepository, TokenSenhaRepository tokenSenhaRepository,
                           RefreshTokenRepository refreshTokenRepository, AdministradoraRepository administradoraRepository,
                           PasswordEncoder passwordEncoder, EmailDispatcher emailDispatcher, EmailsConta emailsConta,
                           AuditoriaService auditoriaService, UsuarioAtualProvider usuarioAtualProvider,
                           TekCondoProperties props, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.tokenSenhaRepository = tokenSenhaRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.administradoraRepository = administradoraRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailDispatcher = emailDispatcher;
        this.emailsConta = emailsConta;
        this.auditoriaService = auditoriaService;
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.props = props;
        this.clock = clock;
    }

    /** Hash de uma senha aleatória descartada: conta criada por convite não tem senha utilizável. */
    public String hashSenhaInutilizavel() {
        return passwordEncoder.encode(tokenAleatorio());
    }

    /** Emite convite (deve ser chamado dentro da transação que criou/alterou o usuário). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enviarConvite(Usuario usuario) {
        String token = emitir(usuario, TokenSenha.Finalidade.INVITE, props.security().inviteTtl());
        String nomeAdministradora = usuario.getIdAdministradora() == null ? null
                : administradoraRepository.findById(usuario.getIdAdministradora()).map(o -> o.getNome()).orElse(null);
        emailDispatcher.enviarAposCommit(emailsConta.convite(usuario, nomeAdministradora, token));
        UsuarioAtual ator = usuarioAtualProvider.obter();
        auditoriaService.registrarComo(usuario.getIdAdministradora(), ator.idUsuario(), ator.email(), AcaoAuditoria.INVITE_SENT, ENTIDADE,
                usuario.getId(), Map.of("email", usuario.getEmail()));
    }

    /**
     * Esqueci a senha. Resposta sempre igual (não revela se o e-mail existe). Limite de solicitações por hora
     * para evitar abuso da caixa de entrada do usuário.
     */
    @Transactional
    public void solicitarRedefinicao(String email) {
        Instant agora = Instant.now(clock);
        Usuario usuario = usuarioRepository.buscarPorEmail(email.trim()).orElse(null);
        if (usuario == null || !podeEntrar(usuario)) {
            return;
        }
        long recentes = tokenSenhaRepository.countByIdUsuarioAndFinalidadeAndCriadoEmAfter(usuario.getId(), TokenSenha.Finalidade.RESET,
                agora.minus(Duration.ofHours(1)));
        if (recentes >= props.security().maxResetRequestsPerHour()) {
            log.warn("Limite de pedidos de redefinição atingido para o usuário {}", usuario.getId());
            return;
        }
        String token = emitir(usuario, TokenSenha.Finalidade.RESET, props.security().passwordResetTtl());
        emailDispatcher.enviarAposCommit(emailsConta.redefinicaoSenha(usuario, token));
        auditoriaService.registrarComo(usuario.getIdAdministradora(), usuario.getId(), usuario.getEmail(),
                AcaoAuditoria.PASSWORD_RESET_REQUESTED, ENTIDADE, usuario.getId(), null);
    }

    /** Informações para a tela de definição de senha (sem consumir o token). */
    @Transactional(readOnly = true)
    public TokenSenhaInfo descrever(String tokenBruto) {
        TokenSenha token = tokenUtilizavel(tokenBruto);
        Usuario usuario = usuarioRepository.findById(token.getIdUsuario()).filter(this::podeEntrar).orElseThrow(SenhaService::linkInvalido);
        return new TokenSenhaInfo(token.getFinalidade(), usuario.getNome(), usuario.getEmail());
    }

    /** Define a nova senha a partir do link. Encerra todas as sessões abertas do usuário. */
    @Transactional
    public void concluir(String tokenBruto, String novaSenha) {
        Instant agora = Instant.now(clock);
        TokenSenha token = tokenUtilizavel(tokenBruto);
        Usuario usuario = usuarioRepository.findById(token.getIdUsuario()).filter(this::podeEntrar).orElseThrow(SenhaService::linkInvalido);
        usuario.alterarSenha(passwordEncoder.encode(novaSenha));
        token.marcarUsado(agora);
        tokenSenhaRepository.invalidarPendentes(usuario.getId(), agora);
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), agora);
        AcaoAuditoria acao = token.getFinalidade() == TokenSenha.Finalidade.INVITE
                ? AcaoAuditoria.INVITE_ACCEPTED : AcaoAuditoria.PASSWORD_RESET_COMPLETED;
        auditoriaService.registrarComo(usuario.getIdAdministradora(), usuario.getId(), usuario.getEmail(), acao, ENTIDADE, usuario.getId(), null);
    }

    /** Troca de senha pelo próprio usuário; mantém a sessão atual e encerra as demais. */
    @Transactional
    public void alterar(String senhaAtual, String novaSenha, String refreshTokenAtual) {
        UsuarioAtual atual = usuarioAtualProvider.obter();
        Usuario usuario = usuarioRepository.findById(atual.idUsuario()).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
        if (!passwordEncoder.matches(senhaAtual, usuario.getHashSenha())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "WRONG_CURRENT_PASSWORD", "A senha atual não confere.");
        }
        if (passwordEncoder.matches(novaSenha, usuario.getHashSenha())) {
            throw ApiException.regraNegocio("SAME_PASSWORD", "A nova senha deve ser diferente da atual.");
        }
        Instant agora = Instant.now(clock);
        usuario.alterarSenha(passwordEncoder.encode(novaSenha));
        if (refreshTokenAtual != null && !refreshTokenAtual.isBlank()) {
            refreshTokenRepository.revogarTodosDoUsuarioExceto(usuario.getId(), TokenService.hash(refreshTokenAtual), agora);
        } else {
            refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), agora);
        }
        tokenSenhaRepository.invalidarPendentes(usuario.getId(), agora);
        auditoriaService.registrarComo(usuario.getIdAdministradora(), usuario.getId(), usuario.getEmail(), AcaoAuditoria.PASSWORD_CHANGED,
                ENTIDADE, usuario.getId(), null);
    }

    private String emitir(Usuario usuario, TokenSenha.Finalidade finalidade, Duration validade) {
        Instant agora = Instant.now(clock);
        tokenSenhaRepository.invalidarPendentes(usuario.getId(), agora);
        String bruto = tokenAleatorio();
        tokenSenhaRepository.save(new TokenSenha(usuario.getId(), finalidade, TokenService.hash(bruto), agora, agora.plus(validade)));
        return bruto;
    }

    private TokenSenha tokenUtilizavel(String tokenBruto) {
        if (tokenBruto == null || tokenBruto.isBlank() || tokenBruto.length() > 200) {
            throw linkInvalido();
        }
        return tokenSenhaRepository.findByTokenHash(TokenService.hash(tokenBruto.trim()))
                .filter(t -> t.isUtilizavel(Instant.now(clock)))
                .orElseThrow(SenhaService::linkInvalido);
    }

    private boolean podeEntrar(Usuario usuario) {
        if (!usuario.isAtivo()) {
            return false;
        }
        return usuario.getIdAdministradora() == null
                || administradoraRepository.findById(usuario.getIdAdministradora()).map(o -> o.isAtivo()).orElse(false);
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ApiException linkInvalido() {
        return new ApiException(HttpStatus.GONE, "INVALID_LINK",
                "Este link é inválido, já foi utilizado ou expirou. Solicite um novo.");
    }
}
