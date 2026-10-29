package br.com.tekcondo.portal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.comunicado.Comunicado;
import br.com.tekcondo.comunicado.ComunicadoRepository;
import br.com.tekcondo.comunicado.ComunicadoService;
import br.com.tekcondo.assembleia.AssembleiaEntities.Assembleia;
import br.com.tekcondo.assembleia.AssembleiaRepository;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.LinhaDebito;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.DebitosUnidade;
import br.com.tekcondo.cobranca.service.InadimplenciaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.repository.CondominioRepository;
import br.com.tekcondo.documento.entity.Documento;
import br.com.tekcondo.documento.repository.DocumentoRepository;
import br.com.tekcondo.documento.service.DocumentoService;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.ocorrencia.Ocorrencia;
import br.com.tekcondo.ocorrencia.InteracaoOcorrencia;
import br.com.tekcondo.ocorrencia.InteracaoOcorrenciaRepository;
import br.com.tekcondo.ocorrencia.OcorrenciaRepository;
import br.com.tekcondo.ocorrencia.OcorrenciaService;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.reserva.ReservaService;
import br.com.tekcondo.portaria.PortariaService;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal do morador. O acesso é derivado SOMENTE do vínculo do usuário com o cadastro de pessoa e das ocupações
 * vigentes: o morador vê apenas suas unidades. Cobranças: apenas para proprietário ou responsável financeiro.
 */
@Service
public class PortalService {

    public record UnidadePortal(UUID idUnidade, String rotulo, Vinculo.Tipo tipo, boolean responsavelFinanceiro, boolean veCobrancas) {
    }

    public record CondominioPortal(UUID id, String nome, List<UnidadePortal> unidades) {
    }

    public record PerfilPortal(String nome, String email, List<CondominioPortal> condominios) {
    }

    public record CobrancasUnidade(UUID idUnidade, String rotuloUnidade, java.math.BigDecimal totalAberto, List<LinhaDebito> linhas) {
    }

    public record EventoCalendario(LocalDate data, String tipo, String titulo) {
    }

    public record AssembleiaPortal(UUID id, String titulo, Assembleia.Tipo tipo, Instant primeiraConvocacaoEm, String local,
                                   Assembleia.Situacao situacao, String ata) {
    }

    private record Contexto(UsuarioAtual usuario, Usuario conta, Condominio condominio, List<Vinculo> vinculos) {

        Set<UUID> idsUnidades() {
            return vinculos.stream().map(o -> o.getUnidade().getId()).collect(Collectors.toSet());
        }
    }

    private final UsuarioAtualProvider usuarioAtualProvider;
    private final UsuarioRepository usuarios;
    private final AcessoService acessoService;
    private final CondominioRepository condominios;
    private final VinculoRepository vinculos;
    private final InadimplenciaService inadimplenciaService;
    private final ComunicadoRepository comunicados;
    private final ComunicadoService comunicadoService;
    private final DocumentoRepository documentos;
    private final DocumentoService documentoService;
    private final OcorrenciaRepository ocorrencias;
    private final InteracaoOcorrenciaRepository interacoes;
    private final OcorrenciaService ocorrenciaService;
    private final AssembleiaRepository assembleias;
    private final ReservaService reservaService;
    private final PortariaService portariaService;
    private final Clock clock;

    public PortalService(UsuarioAtualProvider usuarioAtualProvider, UsuarioRepository usuarios, AcessoService acessoService,
                         CondominioRepository condominios, VinculoRepository vinculos, InadimplenciaService inadimplenciaService,
                         ComunicadoRepository comunicados, ComunicadoService comunicadoService,
                         DocumentoRepository documentos, DocumentoService documentoService, OcorrenciaRepository ocorrencias,
                         InteracaoOcorrenciaRepository interacoes, OcorrenciaService ocorrenciaService,
                         AssembleiaRepository assembleias, ReservaService reservaService, PortariaService portariaService, Clock clock) {
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.usuarios = usuarios;
        this.acessoService = acessoService;
        this.condominios = condominios;
        this.vinculos = vinculos;
        this.inadimplenciaService = inadimplenciaService;
        this.comunicados = comunicados;
        this.comunicadoService = comunicadoService;
        this.documentos = documentos;
        this.documentoService = documentoService;
        this.ocorrencias = ocorrencias;
        this.interacoes = interacoes;
        this.ocorrenciaService = ocorrenciaService;
        this.assembleias = assembleias;
        this.reservaService = reservaService;
        this.portariaService = portariaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PerfilPortal perfil() {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        Usuario conta = contaVinculada(usuario);
        List<CondominioPortal> lista = new ArrayList<>();
        for (UUID idCondominioAcessivel : acessoService.concessoesAtuais().porCondominio().entrySet().stream()
                .filter(e -> e.getValue().contains(Permissao.PORTAL_ACCESS)).map(Map.Entry::getKey).toList()) {
            condominios.findByIdAndIdAdministradora(idCondominioAcessivel, usuario.idAdministradora()).ifPresent(c -> lista.add(new CondominioPortal(
                    c.getId(), c.getNome(), vinculos.buscarVigentesDaPessoaNoCondominio(conta.getIdPessoa(), c.getId()).stream()
                    .map(this::paraUnidade).sorted(Comparator.comparing(UnidadePortal::rotulo)).toList())));
        }
        return new PerfilPortal(conta.getNome(), conta.getEmail(), lista);
    }

    @Transactional(readOnly = true)
    public List<CobrancasUnidade> cobrancas(UUID idCondominio) {
        Contexto contexto = contexto(idCondominio);
        LocalDate hoje = LocalDate.now(clock);
        return contexto.vinculos().stream().filter(o -> paraUnidade(o).veCobrancas()).map(o -> o.getUnidade()).distinct()
                .map(unidade -> {
                    DebitosUnidade debitos = inadimplenciaService.montarDebitosUnidade(idCondominio, unidade, hoje);
                    // Histórico dos últimos 24 meses + tudo que estiver em aberto. Notas internas de cobrança não são expostas.
                    List<LinhaDebito> linhas = debitos.linhas().stream()
                            .filter(l -> l.valorAberto().signum() > 0 || !l.dataVencimento().isBefore(hoje.minusMonths(24)))
                            .sorted(Comparator.comparing(LinhaDebito::dataVencimento).reversed()).toList();
                    return new CobrancasUnidade(unidade.getId(), unidade.rotulo(), debitos.totalAberto(), linhas);
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<ComunicadoService.ComunicadoResponse> comunicados(UUID idCondominio) {
        Contexto contexto = contexto(idCondominio);
        LocalDate hoje = LocalDate.now(clock);
        Set<UUID> blocos = contexto.vinculos().stream().map(o -> o.getUnidade().getBloco()).filter(Objects::nonNull)
                .map(b -> b.getId()).collect(Collectors.toSet());
        return comunicados.buscarDoCondominio(idCondominio).stream()
                .filter(a -> a.isVisivel(hoje))
                .filter(a -> a.getPublicoAlvo() == Comunicado.PublicoAlvo.ALL
                        || a.getPublicoAlvo() == Comunicado.PublicoAlvo.BUILDING && blocos.contains(a.getIdBloco())
                        || a.getPublicoAlvo() == Comunicado.PublicoAlvo.UNIT && contexto.idsUnidades().contains(a.getIdUnidade()))
                .map(a -> comunicadoService.paraResponse(a, null, comunicados.foiLido(a.getId(), contexto.usuario().idUsuario())))
                .toList();
    }

    @Transactional
    public void marcarComunicadoLido(UUID idCondominio, UUID id) {
        Contexto contexto = contexto(idCondominio);
        if (comunicados(idCondominio).stream().noneMatch(a -> a.id().equals(id))) {
            throw ApiException.naoEncontrado("Comunicado");
        }
        comunicados.marcarLido(id, contexto.usuario().idUsuario(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<DocumentoService.DocumentoResponse> documentos(UUID idCondominio) {
        Contexto contexto = contexto(idCondominio);
        return documentosVisiveis(contexto).stream().map(DocumentoService.DocumentoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public DocumentoService.ConteudoArquivo baixarDocumento(UUID idCondominio, UUID id) {
        Contexto contexto = contexto(idCondominio);
        Documento d = documentosVisiveis(contexto).stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.naoEncontrado("Documento"));
        return documentoService.ler(d);
    }

    @Transactional(readOnly = true)
    public List<OcorrenciaService.Resumo> ocorrencias(UUID idCondominio) {
        Contexto contexto = contexto(idCondominio);
        return minhasOcorrencias(contexto).stream().map(ocorrenciaService::resumo).toList();
    }

    @Transactional(readOnly = true)
    public OcorrenciaService.Detalhe ocorrencia(UUID idCondominio, UUID id) {
        Contexto contexto = contexto(idCondominio);
        return ocorrenciaService.detalhe(minhaOcorrencia(contexto, id), false);
    }

    @Transactional
    public OcorrenciaService.Detalhe abrirOcorrencia(UUID idCondominio, OcorrenciaService.CadastroRequest request) {
        Contexto contexto = contexto(idCondominio);
        if (request.idUnidade() != null && !contexto.idsUnidades().contains(request.idUnidade())) {
            throw new AccessDeniedException("Unidade não vinculada ao morador");
        }
        // Prioridade é definida pela administração na triagem.
        var seguro = new OcorrenciaService.CadastroRequest(request.titulo(), request.descricao(), request.categoria(),
                Ocorrencia.Prioridade.MEDIUM, request.idUnidade());
        return ocorrenciaService.detalhe(ocorrenciaService.cadastrarInterno(contexto.condominio(), seguro, contexto.usuario().idUsuario(),
                contexto.conta().getNome()), false);
    }

    @Transactional
    public OcorrenciaService.Detalhe comentarOcorrencia(UUID idCondominio, UUID id, String mensagem) {
        Contexto contexto = contexto(idCondominio);
        Ocorrencia o = minhaOcorrencia(contexto, id);
        if (o.getSituacao() == Ocorrencia.Situacao.RESOLVED || o.getSituacao() == Ocorrencia.Situacao.CANCELLED) {
            throw ApiException.regraNegocio("OCCURRENCE_CLOSED", "A ocorrência está encerrada. Abra uma nova, se necessário.");
        }
        interacoes.save(new InteracaoOcorrencia(o.getId(), contexto.usuario().idUsuario(), contexto.conta().getNome(), mensagem.trim(), null, false));
        return ocorrenciaService.detalhe(o, false);
    }

    @Transactional(readOnly = true)
    public List<AssembleiaPortal> assembleias(UUID idCondominio) {
        contexto(idCondominio);
        return assembleias.findByIdCondominioOrderByPrimeiraConvocacaoEmDesc(idCondominio).stream()
                .filter(a -> a.getSituacao() != Assembleia.Situacao.DRAFT)
                .map(a -> new AssembleiaPortal(a.getId(), a.getTitulo(), a.getTipo(), a.getPrimeiraConvocacaoEm(), a.getLocal(), a.getSituacao(),
                        a.getSituacao() == Assembleia.Situacao.CLOSED ? a.getAta() : null))
                .toList();
    }

    // ------------------------------------------------------------------ reservas de áreas comuns

    @Transactional(readOnly = true)
    public List<ReservaService.AreaComumResponse> areasComuns(UUID idCondominio) {
        contexto(idCondominio);
        return reservaService.areasAtivas(idCondominio);
    }

    /** Ocupação dos horários (sem identificar unidades de terceiros). */
    @Transactional(readOnly = true)
    public List<ReservaService.Horario> ocupacaoArea(UUID idCondominio, UUID idArea, LocalDate de, LocalDate ate) {
        contexto(idCondominio);
        return reservaService.ocupacao(idCondominio, idArea, de, ate);
    }

    @Transactional(readOnly = true)
    public List<ReservaService.ReservaResponse> minhasReservas(UUID idCondominio) {
        Contexto contexto = contexto(idCondominio);
        return reservaService.dasUnidades(idCondominio, contexto.idsUnidades());
    }

    @Transactional
    public ReservaService.ReservaResponse reservar(UUID idCondominio, ReservaService.ReservaRequest request) {
        Contexto contexto = contexto(idCondominio);
        if (!contexto.idsUnidades().contains(request.idUnidade())) {
            throw new AccessDeniedException("Unidade não vinculada ao morador");
        }
        if (contexto.condominio().getSituacao() != br.com.tekcondo.condominio.entity.SituacaoCondominio.ACTIVE) {
            throw ApiException.regraNegocio("CONDOMINIUM_INACTIVE", "O condomínio está inativo.");
        }
        return reservaService.reservarComoMorador(contexto.condominio(), request, contexto.usuario().idUsuario(), contexto.conta().getNome());
    }

    @Transactional
    public ReservaService.ReservaResponse cancelarReserva(UUID idCondominio, UUID id, String motivo) {
        Contexto contexto = contexto(idCondominio);
        return reservaService.cancelarComoMorador(idCondominio, id, contexto.idsUnidades(), motivo);
    }

    // ------------------------------------------------------------------ portaria (autorizações do morador)

    @Transactional(readOnly = true)
    public List<PortariaService.AutorizacaoResponse> autorizacoesVisita(UUID idCondominio) {
        Contexto contexto = contexto(idCondominio);
        return portariaService.dasUnidades(idCondominio, contexto.idsUnidades());
    }

    @Transactional
    public PortariaService.AutorizacaoResponse autorizarVisita(UUID idCondominio, PortariaService.AutorizacaoRequest request) {
        Contexto contexto = contexto(idCondominio);
        if (!contexto.idsUnidades().contains(request.idUnidade())) {
            throw new AccessDeniedException("Unidade não vinculada ao morador");
        }
        return portariaService.autorizarComoMorador(contexto.condominio(), request, contexto.usuario().idUsuario(), contexto.conta().getNome());
    }

    @Transactional
    public PortariaService.AutorizacaoResponse cancelarAutorizacaoVisita(UUID idCondominio, UUID id) {
        Contexto contexto = contexto(idCondominio);
        return portariaService.cancelarComoMorador(idCondominio, id, contexto.idsUnidades());
    }

    /** Calendário: assembleias e vencimentos das cobranças em aberto (de 30 dias atrás a 90 dias à frente). */
    @Transactional(readOnly = true)
    public List<EventoCalendario> calendario(UUID idCondominio) {
        LocalDate hoje = LocalDate.now(clock);
        List<EventoCalendario> eventos = new ArrayList<>();
        for (AssembleiaPortal a : assembleias(idCondominio)) {
            LocalDate d = a.primeiraConvocacaoEm().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
            if (a.situacao() != Assembleia.Situacao.CANCELLED && !d.isBefore(hoje.minusDays(30)) && !d.isAfter(hoje.plusDays(90))) {
                eventos.add(new EventoCalendario(d, "ASSEMBLY", a.titulo() + " — " + a.local()));
            }
        }
        for (CobrancasUnidade u : cobrancas(idCondominio)) {
            for (LinhaDebito l : u.linhas()) {
                if (l.valorAberto().signum() > 0 && !l.dataVencimento().isBefore(hoje.minusDays(30)) && !l.dataVencimento().isAfter(hoje.plusDays(90))) {
                    eventos.add(new EventoCalendario(l.dataVencimento(), "CHARGE", l.descricao() + " · " + u.rotuloUnidade()));
                }
            }
        }
        eventos.sort(Comparator.comparing(EventoCalendario::data));
        return eventos;
    }

    // ------------------------------------------------------------------

    private Contexto contexto(UUID idCondominio) {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        Usuario conta = contaVinculada(usuario);
        Condominio condominio = condominios.findByIdAndIdAdministradora(idCondominio, usuario.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Condomínio"));
        acessoService.exigir(Permissao.PORTAL_ACCESS, idCondominio);
        List<Vinculo> vigentes = vinculos.buscarVigentesDaPessoaNoCondominio(conta.getIdPessoa(), idCondominio);
        if (vigentes.isEmpty()) {
            throw new AccessDeniedException("Sem vínculo vigente com unidades deste condomínio");
        }
        return new Contexto(usuario, conta, condominio, vigentes);
    }

    private Usuario contaVinculada(UsuarioAtual usuario) {
        Usuario conta = usuarios.findById(usuario.idUsuario()).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
        if (conta.getIdPessoa() == null) {
            throw new AccessDeniedException("Usuário não vinculado a cadastro de morador");
        }
        return conta;
    }

    private UnidadePortal paraUnidade(Vinculo o) {
        return new UnidadePortal(o.getUnidade().getId(), o.getUnidade().rotulo(), o.getTipo(), o.isResponsavelFinanceiro(),
                o.getTipo() == Vinculo.Tipo.OWNER || o.isResponsavelFinanceiro());
    }

    private List<Documento> documentosVisiveis(Contexto contexto) {
        return documentos.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("idCondominio"), contexto.condominio().getId()),
                cb.equal(root.get("visibilidade"), Documento.Visibilidade.RESIDENTS),
                cb.isNull(root.get("arquivadoEm")),
                cb.or(cb.isNull(root.get("idUnidade")), root.get("idUnidade").in(contexto.idsUnidades()))),
                Sort.by(Sort.Direction.DESC, "criadoEm"));
    }

    private List<Ocorrencia> minhasOcorrencias(Contexto contexto) {
        return ocorrencias.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("idCondominio"), contexto.condominio().getId()),
                cb.or(cb.equal(root.get("idUsuarioSolicitante"), contexto.usuario().idUsuario()), root.get("idUnidade").in(contexto.idsUnidades()))),
                Sort.by(Sort.Direction.DESC, "criadoEm"));
    }

    private Ocorrencia minhaOcorrencia(Contexto contexto, UUID id) {
        return minhasOcorrencias(contexto).stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow(() -> ApiException.naoEncontrado("Ocorrência"));
    }
}
