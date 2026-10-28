package br.com.tekcondo.condominio.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.dto.CondominioRequest;
import br.com.tekcondo.condominio.dto.CondominioResponse;
import br.com.tekcondo.condominio.dto.CondominioResumo;
import br.com.tekcondo.condominio.dto.OpcaoCondominio;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.condominio.mapper.CondominioMapper;
import br.com.tekcondo.condominio.repository.CondominioRepository;
import br.com.tekcondo.condominio.repository.CondominioSpecifications;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.ConcessoesUsuario;
import br.com.tekcondo.acesso.autorizacao.EscopoAcesso;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CondominioService {

    /** Valor gravado em audit_log.entity_type (dado histórico, permanece em inglês). */
    private static final String ENTIDADE = "Condominium";

    private final CondominioRepository repository;
    private final CondominioMapper mapper;
    private final AcessoService acessoService;
    private final UsuarioAtualProvider usuarioAtualProvider;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public CondominioService(CondominioRepository repository, CondominioMapper mapper, AcessoService acessoService,
                             UsuarioAtualProvider usuarioAtualProvider, AuditoriaService auditoriaService, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.acessoService = acessoService;
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CondominioResumo> listar(String busca, SituacaoCondominio situacao, Pageable pageable) {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        EscopoAcesso escopo = acessoService.exigirEscopo(Permissao.CONDOMINIUM_VIEW);
        Specification<Condominio> spec = Specification.allOf(
                CondominioSpecifications.naAdministradora(usuario.idAdministradora()),
                CondominioSpecifications.noEscopo(escopo),
                CondominioSpecifications.comBusca(busca),
                CondominioSpecifications.comSituacao(situacao));
        return PaginaResponse.of(repository.findAll(spec, pageable), mapper::toResumo);
    }

    /** Condomínios em que o usuário tem alguma permissão, com as permissões efetivas em cada um. */
    @Transactional(readOnly = true)
    public List<OpcaoCondominio> acessiveis() {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        ConcessoesUsuario concessoes = acessoService.concessoesAtuais();
        Set<Permissao> naAdministradora = concessoes.administradoraInteira();
        List<Condominio> condominios = !naAdministradora.isEmpty()
                ? repository.findAll(CondominioSpecifications.naAdministradora(usuario.idAdministradora()),
                        Sort.by("nome"))
                : repository.findAllById(concessoes.porCondominio().keySet()).stream()
                        .filter(c -> c.getIdAdministradora().equals(usuario.idAdministradora()))
                        .sorted(Comparator.comparing(Condominio::getNome)).toList();
        return condominios.stream().map(c -> {
            Set<Permissao> permissoes = EnumSet.noneOf(Permissao.class);
            permissoes.addAll(naAdministradora);
            permissoes.addAll(concessoes.porCondominio().getOrDefault(c.getId(), Set.of()));
            return new OpcaoCondominio(c.getId(), c.getNome(), c.getSituacao(), permissoes);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CondominioResponse obter(UUID id) {
        Condominio condominio = carregar(id);
        acessoService.exigir(Permissao.CONDOMINIUM_VIEW, id);
        return mapper.toResponse(condominio);
    }

    @Transactional
    public CondominioResponse cadastrar(CondominioRequest request) {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        acessoService.exigirNaAdministradora(Permissao.CONDOMINIUM_CREATE);
        String cnpj = DocumentosBrasileiros.normalizarCnpj(request.cnpj());
        if (cnpj != null && repository.existsByIdAdministradoraAndCnpj(usuario.idAdministradora(), cnpj)) {
            throw cnpjDuplicado();
        }
        Condominio condominio = new Condominio(usuario.idAdministradora(), usuario.idUsuario());
        aplicar(condominio, request, cnpj, usuario.idUsuario());
        repository.save(condominio);
        auditoriaService.registrar(AcaoAuditoria.CONDOMINIUM_CREATED, ENTIDADE, condominio.getId(), retrato(condominio));
        return mapper.toResponse(condominio);
    }

    @Transactional
    public CondominioResponse atualizar(UUID id, CondominioRequest request) {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        Condominio condominio = carregar(id);
        acessoService.exigir(Permissao.CONDOMINIUM_EDIT, id);
        if (request.versao() == null) {
            throw ApiException.requisicaoInvalida("VERSION_REQUIRED", "Informe a versão do registro para atualizá-lo.");
        }
        if (!request.versao().equals(condominio.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        String cnpj = DocumentosBrasileiros.normalizarCnpj(request.cnpj());
        if (cnpj != null && repository.existsByIdAdministradoraAndCnpjAndIdNot(usuario.idAdministradora(), cnpj, id)) {
            throw cnpjDuplicado();
        }
        Map<String, Object> antes = retrato(condominio);
        aplicar(condominio, request, cnpj, usuario.idUsuario());
        repository.flush();
        Map<String, Object> alteracoes = diferencas(antes, retrato(condominio));
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(AcaoAuditoria.CONDOMINIUM_UPDATED, ENTIDADE, id, alteracoes);
        }
        return mapper.toResponse(condominio);
    }

    @Transactional
    public CondominioResponse inativar(UUID id, String motivo) {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        Condominio condominio = carregar(id);
        acessoService.exigir(Permissao.CONDOMINIUM_INACTIVATE, id);
        condominio.inativar(motivo.trim(), Instant.now(clock), usuario.idUsuario());
        repository.flush();
        auditoriaService.registrar(AcaoAuditoria.CONDOMINIUM_INACTIVATED, ENTIDADE, id, Map.of("reason", motivo.trim()));
        return mapper.toResponse(condominio);
    }

    @Transactional
    public CondominioResponse ativar(UUID id) {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        Condominio condominio = carregar(id);
        acessoService.exigir(Permissao.CONDOMINIUM_INACTIVATE, id);
        condominio.ativar(usuario.idUsuario());
        repository.flush();
        auditoriaService.registrar(AcaoAuditoria.CONDOMINIUM_ACTIVATED, ENTIDADE, id, null);
        return mapper.toResponse(condominio);
    }

    /** Carrega o condomínio sempre restrito à administradora do usuário (outra administradora = 404). */
    private Condominio carregar(UUID id) {
        UUID idAdministradora = usuarioAtualProvider.exigirMembroAdministradora().idAdministradora();
        return repository.findByIdAndIdAdministradora(id, idAdministradora)
                .orElseThrow(() -> ApiException.naoEncontrado("Condomínio"));
    }

    private void aplicar(Condominio condominio, CondominioRequest request, String cnpj, UUID idUsuario) {
        condominio.atualizarDados(request.nome().trim(), CondominioMapper.aparar(request.razaoSocial()), cnpj,
                request.tipo(), mapper.toEndereco(request.endereco()), CondominioMapper.aparar(request.email()),
                DocumentosBrasileiros.somenteDigitos(request.telefone()), idUsuario);
    }

    private static ApiException cnpjDuplicado() {
        return ApiException.conflito("CONDOMINIUM_CNPJ_TAKEN", "Já existe um condomínio com este CNPJ na sua administradora.");
    }

    /** Chaves em inglês: são os detalhes gravados no histórico de auditoria (dado histórico). */
    private static Map<String, Object> retrato(Condominio c) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("name", c.getNome());
        mapa.put("legalName", c.getRazaoSocial());
        mapa.put("cnpj", c.getCnpj());
        mapa.put("type", c.getTipo() == null ? null : c.getTipo().name());
        mapa.put("address", c.getEndereco() == null ? null : c.getEndereco().toString());
        mapa.put("email", c.getEmail());
        mapa.put("phone", c.getTelefone());
        return mapa;
    }

    private static Map<String, Object> diferencas(Map<String, Object> antes, Map<String, Object> depois) {
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        antes.forEach((campo, valorAnterior) -> {
            Object valorNovo = depois.get(campo);
            if (!Objects.equals(valorAnterior, valorNovo)) {
                Map<String, Object> alteracao = new LinkedHashMap<>();
                alteracao.put("from", valorAnterior);
                alteracao.put("to", valorNovo);
                alteracoes.put(campo, alteracao);
            }
        });
        return alteracoes;
    }
}
