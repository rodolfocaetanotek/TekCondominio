package br.com.tekcondo.administradora.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.acesso.entity.Papel;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.entity.PapelUsuario;
import br.com.tekcondo.acesso.repository.RefreshTokenRepository;
import br.com.tekcondo.acesso.repository.PapelRepository;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.acesso.repository.PapelUsuarioRepository;
import br.com.tekcondo.acesso.service.SenhaService;
import br.com.tekcondo.administradora.dto.AdministradoraRequest;
import br.com.tekcondo.administradora.dto.AdministradoraResponse;
import br.com.tekcondo.administradora.dto.UsuarioAdministradora;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.administradora.repository.AdministradoraRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de administradoras — exclusivo do administrador da plataforma (garantido no SecurityConfig). */
@Service
public class AdministradoraService {

    private final AdministradoraRepository administradoraRepository;
    private final UsuarioRepository usuarioRepository;
    private final PapelRepository papelRepository;
    private final PapelUsuarioRepository papelUsuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SenhaService senhaService;
    private final AuditoriaService auditoriaService;
    private final UsuarioAtualProvider usuarioAtualProvider;

    public AdministradoraService(AdministradoraRepository administradoraRepository, UsuarioRepository usuarioRepository,
                               PapelRepository papelRepository, PapelUsuarioRepository papelUsuarioRepository,
                               RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                               SenhaService senhaService, AuditoriaService auditoriaService,
                               UsuarioAtualProvider usuarioAtualProvider) {
        this.administradoraRepository = administradoraRepository;
        this.usuarioRepository = usuarioRepository;
        this.papelRepository = papelRepository;
        this.papelUsuarioRepository = papelUsuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.senhaService = senhaService;
        this.auditoriaService = auditoriaService;
        this.usuarioAtualProvider = usuarioAtualProvider;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AdministradoraResponse> listar(Pageable pageable) {
        return PaginaResponse.of(administradoraRepository.findAll(pageable), AdministradoraResponse::de);
    }

    @Transactional(readOnly = true)
    public AdministradoraResponse obter(UUID id) {
        return AdministradoraResponse.de(carregar(id));
    }

    @Transactional
    public AdministradoraResponse cadastrar(AdministradoraRequest request) {
        UsuarioAtual ator = usuarioAtualProvider.obter();
        String cnpj = DocumentosBrasileiros.normalizarCnpj(request.cnpj());
        if (administradoraRepository.existsByCnpj(cnpj)) {
            throw ApiException.conflito("ORGANIZATION_CNPJ_TAKEN", "Já existe uma organização com este CNPJ.");
        }
        if (usuarioRepository.existePorEmail(request.administrador().email())) {
            throw ApiException.conflito("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
        }
        Administradora administradora = administradoraRepository.save(new Administradora(request.nome().trim(), cnpj,
                aparadoOuNulo(request.email()), DocumentosBrasileiros.somenteDigitos(request.telefone())));

        Usuario administrador = usuarioRepository.save(Usuario.membroAdministradora(administradora.getId(), request.administrador().nome(),
                request.administrador().email(), passwordEncoder.encode(request.administrador().senha())));
        papelUsuarioRepository.save(new PapelUsuario(administrador.getId(), papelAdministrador(), administradora.getId(), null, ator.idUsuario()));

        auditoriaService.registrarComo(administradora.getId(), ator.idUsuario(), ator.email(), AcaoAuditoria.ORGANIZATION_CREATED,
                "Organization", administradora.getId(), Map.of("name", administradora.getNome(), "cnpj", cnpj));
        auditoriaService.registrarComo(administradora.getId(), ator.idUsuario(), ator.email(), AcaoAuditoria.USER_CREATED,
                "User", administrador.getId(), Map.of("email", administrador.getEmail(), "role", Papel.ORG_ADMIN));
        return AdministradoraResponse.de(administradora);
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdministradora> usuarios(UUID idAdministradora) {
        carregar(idAdministradora);
        return usuarioRepository.findAll(naAdministradora(idAdministradora), Sort.by("nome")).stream()
                .map(u -> new UsuarioAdministradora(u.getId(), u.getNome(), u.getEmail(), u.isAtivo(), u.nuncaAcessou(),
                        u.getUltimoLoginEm(), papelUsuarioRepository.buscarPorUsuarioComPapel(u.getId()).stream()
                        .map(ur -> ur.getPapel().getNome()).distinct().sorted().toList()))
                .toList();
    }

    /** Convida um administrador para a administradora; o titular define a própria senha pelo link do e-mail. */
    @Transactional
    public UsuarioAdministradora convidarAdministrador(UUID idAdministradora, String nome, String email) {
        UsuarioAtual ator = usuarioAtualProvider.obter();
        Administradora administradora = carregar(idAdministradora);
        if (usuarioRepository.existePorEmail(email)) {
            throw ApiException.conflito("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
        }
        Usuario administrador = usuarioRepository.save(Usuario.membroAdministradora(administradora.getId(), nome, email,
                senhaService.hashSenhaInutilizavel()));
        Papel papel = papelAdministrador();
        papelUsuarioRepository.save(new PapelUsuario(administrador.getId(), papel, administradora.getId(), null, ator.idUsuario()));
        auditoriaService.registrarComo(administradora.getId(), ator.idUsuario(), ator.email(), AcaoAuditoria.USER_CREATED,
                "User", administrador.getId(), Map.of("email", administrador.getEmail(), "role", Papel.ORG_ADMIN));
        senhaService.enviarConvite(administrador);
        return new UsuarioAdministradora(administrador.getId(), administrador.getNome(), administrador.getEmail(), true, true, null,
                List.of(papel.getNome()));
    }

    /** Inativar bloqueia o acesso de todos os usuários da administradora; os dados são preservados. */
    @Transactional
    public AdministradoraResponse setAtivo(UUID idAdministradora, boolean ativo) {
        UsuarioAtual ator = usuarioAtualProvider.obter();
        Administradora administradora = carregar(idAdministradora);
        if (administradora.isAtivo() == ativo) {
            throw ApiException.regraNegocio("ORGANIZATION_STATUS_UNCHANGED",
                    ativo ? "A administradora já está ativa." : "A administradora já está inativa.");
        }
        administradora.setAtivo(ativo);
        if (!ativo) {
            Instant agora = Instant.now();
            usuarioRepository.findAll(naAdministradora(idAdministradora))
                    .forEach(u -> refreshTokenRepository.revogarTodosDoUsuario(u.getId(), agora));
        }
        administradoraRepository.flush();
        auditoriaService.registrarComo(idAdministradora, ator.idUsuario(), ator.email(),
                ativo ? AcaoAuditoria.ORGANIZATION_ACTIVATED : AcaoAuditoria.ORGANIZATION_DEACTIVATED,
                "Organization", idAdministradora, null);
        return AdministradoraResponse.de(administradora);
    }

    private Administradora carregar(UUID id) {
        return administradoraRepository.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Organização"));
    }

    private Papel papelAdministrador() {
        return papelRepository.buscarPapelSistema(Papel.ORG_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Papel ORG_ADMIN ausente — verifique as migrações"));
    }

    private static Specification<Usuario> naAdministradora(UUID idAdministradora) {
        return (root, query, cb) -> cb.equal(root.get("idAdministradora"), idAdministradora);
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
