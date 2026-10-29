package br.com.tekcondo.portaria;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portaria: autorizações de visitantes, prestadores e entregas. O morador pré-autoriza pelo portal (recebe um código
 * de 6 dígitos); a portaria registra entrada (pelo código ou na chegada) e saída. O morador é avisado da entrada.
 */
@Service
public class PortariaService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public record AutorizacaoRequest(@NotNull(message = "Informe a unidade.") UUID idUnidade,
                              @NotNull(message = "Informe o tipo.") AutorizacaoVisita.Tipo tipo,
                              @NotBlank(message = "Informe o nome.") @Size(max = 150) String nome,
                              @Size(max = 20) String documento,
                              @Size(max = 120) String empresa,
                              @Pattern(regexp = "^$|^[A-Za-z]{3}-?[0-9][A-Za-z0-9][0-9]{2}$", message = "Placa inválida.") String placaVeiculo,
                              LocalDate validaDe,
                              LocalDate validaAte,
                              @Size(max = 500) String observacoes) {
    }

    public record CodigoRequest(@NotBlank(message = "Informe o código.") @Pattern(regexp = "\\d{6}", message = "O código tem 6 dígitos.") String codigo) {
    }

    /** {@codigo document} sai mascarado (apenas os 3 últimos caracteres). */
    public record AutorizacaoResponse(UUID id, UUID idUnidade, String rotuloUnidade, AutorizacaoVisita.Tipo tipo, String nome, String documento,
                               String empresa, String placaVeiculo, LocalDate validaDe, LocalDate validaAte, String codigoAcesso,
                               AutorizacaoVisita.Situacao situacao, boolean peloMorador, String nomeAutorizadoPor, Instant entradaEm,
                               String nomeEntradaPor, Instant saidaEm, String observacoes) {
    }

    private final AutorizacaoVisitaRepository autorizacoes;
    private final UnidadeRepository unidades;
    private final VinculoRepository vinculos;
    private final UsuarioRepository usuarios;
    private final AcessoCondominio acesso;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public PortariaService(AutorizacaoVisitaRepository autorizacoes, UnidadeRepository unidades, VinculoRepository vinculos,
                          UsuarioRepository usuarios, AcessoCondominio acesso, NotificacaoService notificacoes, AuditoriaService auditoria, Clock clock) {
        this.autorizacoes = autorizacoes;
        this.unidades = unidades;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.acesso = acesso;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ portaria

    @Transactional(readOnly = true)
    public List<AutorizacaoResponse> hoje(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.ACCESS_VIEW);
        LocalDate dia = dataHoje();
        Instant inicio = dia.atStartOfDay(FUSO).toInstant();
        return autorizacoes.buscarDoDia(idCondominio, dia, inicio, dia.plusDays(1).atStartOfDay(FUSO).toInstant()).stream()
                .map(v -> paraResponse(v, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<AutorizacaoResponse> historico(UUID idCondominio, LocalDate de, LocalDate ate) {
        acesso.exigir(idCondominio, Permissao.ACCESS_VIEW);
        LocalDate fim = ate == null ? dataHoje() : ate;
        LocalDate inicio = de == null ? fim.minusDays(30) : de;
        if (inicio.isAfter(fim) || inicio.plusDays(366).isBefore(fim)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "Informe um período de até 1 ano.");
        }
        return autorizacoes.buscarCriadasEntre(idCondominio, inicio.atStartOfDay(FUSO).toInstant(), fim.plusDays(1).atStartOfDay(FUSO).toInstant())
                .stream().map(v -> paraResponse(v, false)).toList();
    }

    /** Chegada sem pré-autorização: a portaria registra e a entrada já fica marcada. */
    @Transactional
    public AutorizacaoResponse registrarChegada(UUID idCondominio, AutorizacaoRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.ACCESS_EDIT);
        var usuario = acesso.usuario();
        LocalDate dia = dataHoje();
        AutorizacaoVisita v = cadastrar(condominio, request, dia, dia, false, usuario.idUsuario(), usuario.nome());
        v.registrarEntrada(dia, Instant.now(clock), usuario.nome());
        autorizacoes.flush();
        aposEntrada(v);
        return paraResponse(v, false);
    }

    @Transactional
    public AutorizacaoResponse registrarEntradaPorCodigo(UUID idCondominio, String codigo) {
        acesso.exigirAtivo(idCondominio, Permissao.ACCESS_EDIT);
        AutorizacaoVisita v = autorizacoes.buscarEsperadaPorCodigo(idCondominio, codigo)
                .orElseThrow(() -> ApiException.naoEncontrado("Autorização com este código"));
        return registrarEntrada(v);
    }

    @Transactional
    public AutorizacaoResponse registrarEntrada(UUID idCondominio, UUID id) {
        acesso.exigirAtivo(idCondominio, Permissao.ACCESS_EDIT);
        return registrarEntrada(carregar(idCondominio, id));
    }

    @Transactional
    public AutorizacaoResponse registrarSaida(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.ACCESS_EDIT);
        AutorizacaoVisita v = carregar(idCondominio, id);
        v.registrarSaida(Instant.now(clock), acesso.usuario().nome());
        autorizacoes.flush();
        auditoria.registrar(AcaoAuditoria.VISITOR_LEFT, "VisitorPass", v.getId(), Map.of("unit", rotuloUnidade(v)));
        return paraResponse(v, false);
    }

    // ------------------------------------------------------------------ portal (unidades já validadas pelo chamador)

    public AutorizacaoResponse autorizarComoMorador(Condominio condominio, AutorizacaoRequest request, UUID idUsuario, String nomeUsuario) {
        LocalDate de = request.validaDe() == null ? dataHoje() : request.validaDe();
        LocalDate ate = request.validaAte() == null ? de : request.validaAte();
        if (de.isBefore(dataHoje())) {
            throw ApiException.regraNegocio("PASS_IN_PAST", "A autorização não pode começar no passado.");
        }
        if (ate.isBefore(de) || de.plusDays(90).isBefore(ate)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "Período de validade de até 90 dias.");
        }
        AutorizacaoVisita v = cadastrar(condominio, request, de, ate, true, idUsuario, nomeUsuario);
        v.definirCodigo(codigoUnico(condominio.getId()));
        autorizacoes.flush();
        return paraResponse(v, true);
    }

    public List<AutorizacaoResponse> dasUnidades(UUID idCondominio, Collection<UUID> idsUnidades) {
        return autorizacoes.buscarDasUnidades(idCondominio, idsUnidades).stream().map(v -> paraResponse(v, true)).toList();
    }

    public AutorizacaoResponse cancelarComoMorador(UUID idCondominio, UUID id, Collection<UUID> idsUnidades) {
        AutorizacaoVisita v = autorizacoes.findByIdAndIdCondominio(id, idCondominio).filter(x -> idsUnidades.contains(x.getIdUnidade()))
                .orElseThrow(() -> ApiException.naoEncontrado("Autorização"));
        v.cancelar();
        auditoria.registrar(AcaoAuditoria.VISITOR_PASS_CANCELLED, "VisitorPass", id, Map.of("unit", rotuloUnidade(v)));
        return paraResponse(v, true);
    }

    // ------------------------------------------------------------------ LGPD

    /** Anonimiza os registros cujo período terminou há mais de {@code diasRetencao} dias. */
    @Transactional
    public int anonimizarAnterioresA(int diasRetencao) {
        Instant agora = Instant.now(clock);
        List<AutorizacaoVisita> lista = autorizacoes.buscarParaAnonimizar(dataHoje().minusDays(diasRetencao));
        lista.forEach(v -> v.anonimizar(agora));
        return lista.size();
    }

    // ------------------------------------------------------------------

    private AutorizacaoVisita cadastrar(Condominio condominio, AutorizacaoRequest r, LocalDate de, LocalDate ate, boolean peloMorador,
                               UUID idUsuario, String nomeUsuario) {
        Unidade unidade = unidades.findByIdAndIdCondominio(r.idUnidade(), condominio.getId()).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        AutorizacaoVisita v = autorizacoes.save(new AutorizacaoVisita(condominio.getIdAdministradora(), condominio.getId(), unidade.getId(), r.tipo(),
                r.nome().trim(), aparadoOuNulo(r.documento()), aparadoOuNulo(r.empresa()),
                aparadoOuNulo(r.placaVeiculo()) == null ? null : r.placaVeiculo().replace("-", "").toUpperCase(Locale.ROOT),
                de, ate, aparadoOuNulo(r.observacoes()), peloMorador, idUsuario, nomeUsuario));
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("unit", unidade.rotulo());
        detalhes.put("kind", r.tipo().name());
        detalhes.put("validFrom", de.toString());
        detalhes.put("validUntil", ate.toString());
        auditoria.registrar(AcaoAuditoria.VISITOR_PASS_CREATED, "VisitorPass", v.getId(), detalhes);
        return v;
    }

    private AutorizacaoResponse registrarEntrada(AutorizacaoVisita v) {
        v.registrarEntrada(dataHoje(), Instant.now(clock), acesso.usuario().nome());
        autorizacoes.flush();
        aposEntrada(v);
        return paraResponse(v, false);
    }

    /** Auditoria e aviso aos moradores da unidade com acesso ao portal. */
    private void aposEntrada(AutorizacaoVisita v) {
        auditoria.registrar(AcaoAuditoria.VISITOR_ENTERED, "VisitorPass", v.getId(), Map.of("unit", rotuloUnidade(v), "kind", v.getTipo().name()));
        List<UUID> comPortal = notificacoes.usuariosComPermissao(v.getIdAdministradora(), v.getIdCondominio(), Permissao.PORTAL_ACCESS);
        List<UUID> moradores = vinculos.buscarVigentesPorUnidades(List.of(v.getIdUnidade())).stream()
                .map(o -> usuarios.findByIdPessoa(o.getPessoa().getId())).flatMap(java.util.Optional::stream)
                .map(u -> u.getId()).filter(comPortal::contains).distinct().toList();
        notificacoes.notificar(v.getIdAdministradora(), moradores, switch (v.getTipo()) {
            case VISITOR -> "Visitante na portaria";
            case SERVICE_PROVIDER -> "Prestador de serviço na portaria";
            case DELIVERY -> "Entrega na portaria";
        }, v.getNome() + " — entrada registrada para a unidade " + rotuloUnidade(v), "/portal", null);
    }

    private String codigoUnico(UUID idCondominio) {
        for (int i = 0; i < 20; i++) {
            String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
            if (autorizacoes.buscarEsperadaPorCodigo(idCondominio, codigo).isEmpty()) {
                return codigo;
            }
        }
        throw new IllegalStateException("Não foi possível gerar código de acesso único");
    }

    private AutorizacaoVisita carregar(UUID idCondominio, UUID id) {
        return autorizacoes.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Autorização"));
    }

    private String rotuloUnidade(AutorizacaoVisita v) {
        return unidades.findById(v.getIdUnidade()).map(Unidade::rotulo).orElse("?");
    }

    /** O código só é exibido a quem autorizou (portal); a portaria digita o código informado pelo visitante. */
    private AutorizacaoResponse paraResponse(AutorizacaoVisita v, boolean exibirCodigo) {
        return new AutorizacaoResponse(v.getId(), v.getIdUnidade(), rotuloUnidade(v), v.getTipo(), v.getNome(), mascarar(v.getDocumento()), v.getEmpresa(),
                v.getPlacaVeiculo(), v.getValidaDe(), v.getValidaAte(), exibirCodigo ? v.getCodigoAcesso() : null, v.getSituacao(),
                v.isPeloMorador(), v.getNomeAutorizadoPor(), v.getEntradaEm(), null, v.getSaidaEm(), v.getObservacoes());
    }

    static String mascarar(String documento) {
        if (documento == null) return null;
        String d = documento.replaceAll("[^0-9A-Za-z]", "");
        return d.length() <= 3 ? "***" : "*".repeat(Math.min(d.length() - 3, 8)) + d.substring(d.length() - 3);
    }

    private LocalDate dataHoje() {
        return LocalDate.now(clock.withZone(FUSO));
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
