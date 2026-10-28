package br.com.tekcondo.acesso.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.repository.CondominioRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.acesso.dto.UsuarioDtos;
import br.com.tekcondo.acesso.entity.Papel;
import br.com.tekcondo.acesso.entity.EscopoPapel;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.entity.PapelUsuario;
import br.com.tekcondo.acesso.repository.RefreshTokenRepository;
import br.com.tekcondo.acesso.repository.PapelRepository;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.acesso.repository.PapelUsuarioRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import br.com.tekcondo.comum.web.PaginaResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de usuários da administradora: cadastro por convite, ativação, papéis por organização ou condomínio.
 * Exige USER_VIEW / USER_MANAGE válidos para toda a organização. Garante que a administradora nunca fique
 * sem ao menos um administrador ativo.
 */
@Service
public class UsuarioService {

    private static final String ENTIDADE = "User";

    private final UsuarioRepository usuarioRepository;
    private final PapelRepository papelRepository;
    private final PapelUsuarioRepository papelUsuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CondominioRepository condominioRepository;
    private final SenhaService senhaService;
    private final AcessoService acessoService;
    private final UsuarioAtualProvider usuarioAtualProvider;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public UsuarioService(UsuarioRepository usuarioRepository, PapelRepository papelRepository,
                                 PapelUsuarioRepository papelUsuarioRepository, RefreshTokenRepository refreshTokenRepository,
                                 CondominioRepository condominioRepository, SenhaService senhaService,
                                 AcessoService acessoService, UsuarioAtualProvider usuarioAtualProvider,
                                 AuditoriaService auditoriaService, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.papelRepository = papelRepository;
        this.papelUsuarioRepository = papelUsuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.condominioRepository = condominioRepository;
        this.senhaService = senhaService;
        this.acessoService = acessoService;
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioDtos.Resumo> listar(String busca, Boolean ativo, Pageable pageable) {
        UUID idAdministradora = usuarioAtualProvider.exigirMembroAdministradora().idAdministradora();
        acessoService.exigirNaAdministradora(Permissao.USER_VIEW);
        Specification<Usuario> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("idAdministradora"), idAdministradora);
            if (busca != null && !busca.isBlank()) {
                String like = "%" + busca.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                p = cb.and(p, cb.or(cb.like(cb.lower(root.get("nome")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')));
            }
            if (ativo != null) {
                p = cb.and(p, cb.equal(root.get("ativo"), ativo));
            }
            return p;
        };
        Page<Usuario> pagina = usuarioRepository.findAll(spec, pageable);
        Map<UUID, List<String>> papeis = papelUsuarioRepository
                .buscarPorUsuariosComPapel(pagina.getContent().stream().map(Usuario::getId).toList()).stream()
                .collect(Collectors.groupingBy(PapelUsuario::getIdUsuario,
                        Collectors.mapping(ur -> ur.getPapel().getNome(), Collectors.toList())));
        return PaginaResponse.of(pagina, u -> new UsuarioDtos.Resumo(u.getId(), u.getNome(), u.getEmail(), u.isAtivo(),
                u.nuncaAcessou(), u.getUltimoLoginEm(), papeis.getOrDefault(u.getId(), List.of()).stream().distinct().sorted().toList()));
    }

    @Transactional(readOnly = true)
    public UsuarioDtos.Detalhe obter(UUID id) {
        acessoService.exigirNaAdministradora(Permissao.USER_VIEW);
        return paraDetalhe(carregar(id));
    }

    @Transactional(readOnly = true)
    public List<UsuarioDtos.PapelResponse> papeis() {
        UUID idAdministradora = usuarioAtualProvider.exigirMembroAdministradora().idAdministradora();
        acessoService.exigirNaAdministradora(Permissao.USER_VIEW);
        return papelRepository.buscarDisponiveisPara(idAdministradora).stream()
                .map(r -> new UsuarioDtos.PapelResponse(r.getCodigo(), r.getNome(), r.getDescricao(), r.getEscopo(), r.getPermissoes()))
                .toList();
    }

    @Transactional
    public UsuarioDtos.Detalhe cadastrar(UsuarioDtos.CadastroRequest request) {
        UsuarioAtual ator = usuarioAtualProvider.exigirMembroAdministradora();
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        if (usuarioRepository.existePorEmail(request.email())) {
            throw ApiException.conflito("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
        }
        // Valida o papel antes de criar, para não deixar usuário sem acesso em caso de erro.
        Papel papel = resolverPapel(request.atribuicao().codigoPapel());
        validarEscopo(papel, request.atribuicao().idCondominio(), ator.idAdministradora());

        Usuario usuario = usuarioRepository.save(Usuario.membroAdministradora(ator.idAdministradora(), request.nome(), request.email(),
                senhaService.hashSenhaInutilizavel()));
        auditoriaService.registrar(AcaoAuditoria.USER_CREATED, ENTIDADE, usuario.getId(),
                Map.of("name", usuario.getNome(), "email", usuario.getEmail()));
        atribuir(usuario, papel, request.atribuicao().idCondominio(), ator);
        senhaService.enviarConvite(usuario);
        return paraDetalhe(usuario);
    }

    @Transactional
    public UsuarioDtos.Detalhe atualizar(UUID id, UsuarioDtos.AtualizacaoRequest request) {
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Usuario usuario = carregar(id);
        if (!request.versao().equals(usuario.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        String antes = usuario.getNome();
        usuario.renomear(request.nome());
        usuarioRepository.flush();
        if (!antes.equals(usuario.getNome())) {
            auditoriaService.registrar(AcaoAuditoria.USER_UPDATED, ENTIDADE, id,
                    Map.of("name", Map.of("from", antes, "to", usuario.getNome())));
        }
        return paraDetalhe(usuario);
    }

    @Transactional
    public UsuarioDtos.Detalhe desativar(UUID id) {
        UsuarioAtual ator = usuarioAtualProvider.exigirMembroAdministradora();
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Usuario usuario = carregar(id);
        if (usuario.getId().equals(ator.idUsuario())) {
            throw ApiException.regraNegocio("CANNOT_DEACTIVATE_SELF", "Você não pode desativar o próprio usuário.");
        }
        if (!usuario.isAtivo()) {
            throw ApiException.regraNegocio("USER_ALREADY_INACTIVE", "O usuário já está inativo.");
        }
        garantirNaoUltimoAdministrador(usuario, papelUsuarioRepository.buscarPorUsuarioComPapel(usuario.getId()));
        usuario.desativar();
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), Instant.now(clock));
        usuarioRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.USER_DEACTIVATED, ENTIDADE, id, null);
        return paraDetalhe(usuario);
    }

    @Transactional
    public UsuarioDtos.Detalhe ativar(UUID id) {
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Usuario usuario = carregar(id);
        if (usuario.isAtivo()) {
            throw ApiException.regraNegocio("USER_ALREADY_ACTIVE", "O usuário já está ativo.");
        }
        usuario.ativar();
        usuarioRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.USER_ACTIVATED, ENTIDADE, id, null);
        return paraDetalhe(usuario);
    }

    @Transactional
    public UsuarioDtos.Detalhe adicionarAtribuicao(UUID id, UsuarioDtos.AtribuicaoRequest request) {
        UsuarioAtual ator = usuarioAtualProvider.exigirMembroAdministradora();
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Usuario usuario = carregar(id);
        Papel papel = resolverPapel(request.codigoPapel());
        validarEscopo(papel, request.idCondominio(), ator.idAdministradora());
        atribuir(usuario, papel, request.idCondominio(), ator);
        return paraDetalhe(usuario);
    }

    @Transactional
    public UsuarioDtos.Detalhe removerAtribuicao(UUID id, UUID idAtribuicao) {
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Usuario usuario = carregar(id);
        List<PapelUsuario> atual = papelUsuarioRepository.buscarPorUsuarioComPapel(usuario.getId());
        PapelUsuario alvo = atual.stream().filter(ur -> ur.getId().equals(idAtribuicao)).findFirst()
                .orElseThrow(() -> ApiException.naoEncontrado("Atribuição de papel"));
        if (isAdministradorDaAdministradora(alvo) && usuario.isAtivo()
                && papelUsuarioRepository.contarAdministradoresAtivosDaAdministradora(usuario.getIdAdministradora()) <= 1) {
            throw ultimoAdministrador();
        }
        papelUsuarioRepository.delete(alvo);
        auditoriaService.registrar(AcaoAuditoria.ROLE_REVOKED, ENTIDADE, id, detalhesAtribuicao(alvo.getPapel(), alvo.getIdCondominio()));
        papelUsuarioRepository.flush();
        return paraDetalhe(usuario);
    }

    @Transactional
    public void reenviarConvite(UUID id) {
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Usuario usuario = carregar(id);
        if (!usuario.isAtivo() || !usuario.nuncaAcessou()) {
            throw ApiException.regraNegocio("INVITE_NOT_APPLICABLE",
                    "O convite só pode ser reenviado para usuários ativos que ainda não acessaram o sistema.");
        }
        senhaService.enviarConvite(usuario);
    }

    // ------------------------------------------------------------------

    private Usuario carregar(UUID id) {
        UUID idAdministradora = usuarioAtualProvider.exigirMembroAdministradora().idAdministradora();
        return usuarioRepository.findByIdAndIdAdministradora(id, idAdministradora).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
    }

    private Papel resolverPapel(String codigo) {
        return papelRepository.buscarPapelSistema(codigo.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.regraNegocio("ROLE_NOT_FOUND", "Papel inexistente: " + codigo));
    }

    private void validarEscopo(Papel papel, UUID idCondominio, UUID idAdministradora) {
        if (papel.getEscopo() == EscopoPapel.ORGANIZATION && idCondominio != null) {
            throw ApiException.regraNegocio("ROLE_SCOPE_MISMATCH",
                    "O papel \"" + papel.getNome() + "\" vale para toda a administradora e não deve ter condomínio.");
        }
        if (papel.getEscopo() == EscopoPapel.CONDOMINIUM) {
            if (idCondominio == null) {
                throw ApiException.regraNegocio("ROLE_SCOPE_MISMATCH",
                        "Informe o condomínio para o papel \"" + papel.getNome() + "\".");
            }
            condominioRepository.findByIdAndIdAdministradora(idCondominio, idAdministradora)
                    .orElseThrow(() -> ApiException.naoEncontrado("Condomínio"));
        }
    }

    private void atribuir(Usuario usuario, Papel papel, UUID idCondominio, UsuarioAtual ator) {
        boolean existe = idCondominio == null
                ? papelUsuarioRepository.existeNaAdministradora(usuario.getId(), papel.getId())
                : papelUsuarioRepository.existeNoCondominio(usuario.getId(), papel.getId(), idCondominio);
        if (existe) {
            throw ApiException.conflito("ROLE_ALREADY_ASSIGNED", "O usuário já possui este papel neste escopo.");
        }
        papelUsuarioRepository.save(new PapelUsuario(usuario.getId(), papel, ator.idAdministradora(), idCondominio, ator.idUsuario()));
        auditoriaService.registrar(AcaoAuditoria.ROLE_ASSIGNED, ENTIDADE, usuario.getId(), detalhesAtribuicao(papel, idCondominio));
    }

    private void garantirNaoUltimoAdministrador(Usuario usuario, List<PapelUsuario> atribuicoes) {
        boolean isAdministrador = atribuicoes.stream().anyMatch(this::isAdministradorDaAdministradora);
        if (isAdministrador && papelUsuarioRepository.contarAdministradoresAtivosDaAdministradora(usuario.getIdAdministradora()) <= 1) {
            throw ultimoAdministrador();
        }
    }

    private boolean isAdministradorDaAdministradora(PapelUsuario ur) {
        return ur.getIdCondominio() == null && Papel.ORG_ADMIN.equals(ur.getPapel().getCodigo());
    }

    private static ApiException ultimoAdministrador() {
        return ApiException.regraNegocio("LAST_ADMIN",
                "A administradora precisa de ao menos um administrador ativo. Designe outro administrador antes.");
    }

    private static Map<String, Object> detalhesAtribuicao(Papel papel, UUID idCondominio) {
        return idCondominio == null ? Map.of("role", papel.getCodigo())
                : Map.of("role", papel.getCodigo(), "condominiumId", idCondominio.toString());
    }

    private UsuarioDtos.Detalhe paraDetalhe(Usuario usuario) {
        List<PapelUsuario> atribuicoes = papelUsuarioRepository.buscarPorUsuarioComPapel(usuario.getId());
        Map<UUID, String> nomesCondominios = nomesCondominios(atribuicoes.stream()
                .map(PapelUsuario::getIdCondominio).filter(java.util.Objects::nonNull).collect(Collectors.toSet()),
                usuario.getIdAdministradora());
        List<UsuarioDtos.Atribuicao> itens = atribuicoes.stream()
                .map(ur -> new UsuarioDtos.Atribuicao(ur.getId(), ur.getPapel().getCodigo(), ur.getPapel().getNome(),
                        ur.getPapel().getEscopo(), ur.getIdCondominio(),
                        ur.getIdCondominio() == null ? null : nomesCondominios.get(ur.getIdCondominio())))
                .sorted(Comparator.comparing((UsuarioDtos.Atribuicao a) -> a.escopo().ordinal())
                        .thenComparing(a -> a.nomeCondominio() == null ? "" : a.nomeCondominio())
                        .thenComparing(UsuarioDtos.Atribuicao::nomePapel))
                .toList();
        return new UsuarioDtos.Detalhe(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.isAtivo(),
                usuario.nuncaAcessou(), usuario.getUltimoLoginEm(), usuario.getCriadoEm(), usuario.getVersao(), itens);
    }

    private Map<UUID, String> nomesCondominios(Collection<UUID> ids, UUID idAdministradora) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> desejados = new HashSet<>(ids);
        return condominioRepository.findAllById(desejados).stream()
                .filter(c -> c.getIdAdministradora().equals(idAdministradora))
                .collect(Collectors.toMap(Condominio::getId, Condominio::getNome, (a, b) -> a));
    }

}
