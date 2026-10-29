package br.com.tekcondo.reserva;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.ContaPlanoRepository;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.financeiro.service.BloqueioPeriodo;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservas de áreas comuns. Regras por área: horário de funcionamento, duração máxima, antecedência mínima/máxima
 * (para moradores), limite mensal por unidade, bloqueio de unidade inadimplente e aprovação prévia. A taxa de uso,
 * quando houver, vira cobrança na unidade ao confirmar a reserva.
 */
@Service
public class ReservaService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String CODIGO_CONTA_TAXA_AREA = "1.06";
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record AreaComumRequest(@NotBlank(message = "Informe o nome.") @Size(max = 80) String nome,
                              @Size(max = 1000) String descricao,
                              @Size(max = 2000) String regras,
                              @Min(1) Integer capacidade,
                              @DecimalMin(value = "0", message = "A taxa não pode ser negativa.") @Digits(integer = 13, fraction = 2) BigDecimal taxa,
                              boolean exigeAprovacao,
                              boolean bloqueiaInadimplente,
                              @NotNull(message = "Informe o horário de abertura.") LocalTime horaAbertura,
                              @NotNull(message = "Informe o horário de fechamento.") LocalTime horaFechamento,
                              @Min(15) @Max(1440) Integer duracaoMaximaMinutos,
                              @Min(0) @Max(720) Integer antecedenciaMinimaHoras,
                              @Min(1) @Max(730) Integer antecedenciaMaximaDias,
                              @Min(1) Integer limiteMensalPorUnidade,
                              Boolean ativa) {
    }

    public record AreaComumResponse(UUID id, String nome, String descricao, String regras, Integer capacidade, BigDecimal taxa,
                               boolean exigeAprovacao, boolean bloqueiaInadimplente, LocalTime horaAbertura, LocalTime horaFechamento,
                               int duracaoMaximaMinutos, int antecedenciaMinimaHoras, int antecedenciaMaximaDias, Integer limiteMensalPorUnidade,
                               boolean ativa, Long versao) {
    }

    public record ReservaRequest(@NotNull(message = "Informe a área.") UUID idArea,
                                     @NotNull(message = "Informe a unidade.") UUID idUnidade,
                                     @NotNull(message = "Informe a data.") LocalDate data,
                                     @NotNull(message = "Informe o horário inicial.") LocalTime horaInicio,
                                     @NotNull(message = "Informe o horário final.") LocalTime horaFim,
                                     @Min(0) @Max(10000) Integer convidados,
                                     @Size(max = 500) String observacoes) {
    }

    public record ReservaResponse(UUID id, UUID idArea, String nomeArea, UUID idUnidade, String rotuloUnidade, LocalDate data,
                                      LocalTime horaInicio, LocalTime horaFim, Integer convidados, String observacoes, Reserva.Situacao situacao,
                                      BigDecimal taxa, UUID idLancamento, String nomeSolicitante, boolean peloMorador, String observacaoDecisao,
                                      String motivoCancelamento, Instant criadoEm) {
    }

    /** Ocupação de horário sem identificar a unidade (para o portal do morador). */
    public record Horario(UUID idArea, LocalDate data, LocalTime horaInicio, LocalTime horaFim, Reserva.Situacao situacao) {
    }

    public record DecisaoRequest(@Size(max = 255) String observacao) {
    }

    public record CancelamentoRequest(@NotBlank(message = "Informe o motivo.") @Size(min = 3, max = 255) String motivo) {
    }

    private final AreaComumRepository areas;
    private final ReservaRepository reservas;
    private final UnidadeRepository unidades;
    private final VinculoRepository vinculos;
    private final LancamentoRepository lancamentos;
    private final ContaPlanoRepository contasPlano;
    private final BloqueioPeriodo bloqueioPeriodo;
    private final AcessoCondominio acesso;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ReservaService(AreaComumRepository areas, ReservaRepository reservas, UnidadeRepository unidades,
                              VinculoRepository vinculos, LancamentoRepository lancamentos, ContaPlanoRepository contasPlano,
                              BloqueioPeriodo bloqueioPeriodo, AcessoCondominio acesso, NotificacaoService notificacoes,
                              AuditoriaService auditoria, Clock clock) {
        this.areas = areas;
        this.reservas = reservas;
        this.unidades = unidades;
        this.vinculos = vinculos;
        this.lancamentos = lancamentos;
        this.contasPlano = contasPlano;
        this.bloqueioPeriodo = bloqueioPeriodo;
        this.acesso = acesso;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ áreas

    @Transactional(readOnly = true)
    public List<AreaComumResponse> listarAreas(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.RESERVATION_VIEW);
        return areas.findByIdCondominioOrderByNomeAsc(idCondominio).stream().map(ReservaService::paraAreaResponse).toList();
    }

    @Transactional
    public AreaComumResponse salvarArea(UUID idCondominio, UUID id, AreaComumRequest r) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.RESERVATION_EDIT);
        if (!r.horaFechamento().isAfter(r.horaAbertura())) {
            throw ApiException.requisicaoInvalida("INVALID_HOURS", "O horário de fechamento deve ser posterior ao de abertura.");
        }
        AreaComum area = id == null ? new AreaComum(condominio.getIdAdministradora(), idCondominio)
                : areas.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Área comum"));
        if (areas.nomeEmUso(idCondominio, r.nome().trim(), area.getId())) {
            throw ApiException.conflito("AREA_NAME_TAKEN", "Já existe uma área com este nome.");
        }
        area.atualizar(r.nome().trim(), aparadoOuNulo(r.descricao()), aparadoOuNulo(r.regras()), r.capacidade(), r.taxa(), r.exigeAprovacao(),
                r.bloqueiaInadimplente(), r.horaAbertura(), r.horaFechamento(), r.duracaoMaximaMinutos() == null ? 720 : r.duracaoMaximaMinutos(),
                r.antecedenciaMinimaHoras() == null ? 24 : r.antecedenciaMinimaHoras(), r.antecedenciaMaximaDias() == null ? 90 : r.antecedenciaMaximaDias(),
                r.limiteMensalPorUnidade());
        if (r.ativa() != null) {
            area.setAtiva(r.ativa());
        }
        areas.saveAndFlush(area);
        auditoria.registrar(AcaoAuditoria.COMMON_AREA_SAVED, "CommonArea", area.getId(), Map.of("name", area.getNome(),
                "fee", area.getTaxa().toPlainString(), "active", area.isAtiva()));
        return paraAreaResponse(area);
    }

    // ------------------------------------------------------------------ reservas (administração)

    @Transactional(readOnly = true)
    public List<ReservaResponse> listar(UUID idCondominio, UUID idArea, LocalDate de, LocalDate ate) {
        acesso.exigir(idCondominio, Permissao.RESERVATION_VIEW);
        LocalDate inicio = de == null ? hoje().minusDays(7) : de;
        LocalDate fim = ate == null ? inicio.plusDays(60) : ate;
        return reservas.buscarNoPeriodo(idCondominio, idArea, inicio, fim).stream().map(this::paraResponse).toList();
    }

    /** Reserva lançada pela administração (ex.: pedido por telefone): dispensa aprovação e antecedência mínima. */
    @Transactional
    public ReservaResponse cadastrar(UUID idCondominio, ReservaRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.RESERVATION_EDIT);
        var usuario = acesso.usuario();
        return paraResponse(reservar(condominio, request, usuario.idUsuario(), usuario.nome(), false));
    }

    @Transactional
    public ReservaResponse decidir(UUID idCondominio, UUID id, boolean aprovada, String observacao) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.RESERVATION_EDIT);
        Reserva r = carregar(idCondominio, id);
        if (!aprovada && (observacao == null || observacao.isBlank())) {
            throw ApiException.requisicaoInvalida("REASON_REQUIRED", "Informe o motivo da recusa.");
        }
        r.decidir(aprovada, aparadoOuNulo(observacao), acesso.usuario().idUsuario(), Instant.now(clock));
        if (aprovada) {
            cobrarTaxa(condominio, r);
        }
        reservas.flush();
        auditoria.registrar(AcaoAuditoria.RESERVATION_DECIDED, "Reservation", id, Map.of("approved", aprovada, "note", String.valueOf(observacao)));
        if (r.getSolicitadoPor() != null) {
            notificacoes.notificar(r.getIdAdministradora(), List.of(r.getSolicitadoPor()),
                    "Reserva " + (aprovada ? "confirmada" : "recusada"), descrever(r) + (observacao == null || observacao.isBlank() ? "" : " — " + observacao.trim()),
                    "/portal", null);
        }
        return paraResponse(r);
    }

    @Transactional
    public ReservaResponse cancelar(UUID idCondominio, UUID id, String motivo) {
        acesso.exigir(idCondominio, Permissao.RESERVATION_EDIT);
        Reserva r = carregar(idCondominio, id);
        cancelarInterno(r, motivo);
        if (r.getSolicitadoPor() != null && r.isPeloMorador()) {
            notificacoes.notificar(r.getIdAdministradora(), List.of(r.getSolicitadoPor()), "Reserva cancelada pela administração",
                    descrever(r) + " — " + motivo.trim(), "/portal", null);
        }
        return paraResponse(r);
    }

    // ------------------------------------------------------------------ portal (unidade já validada pelo chamador)

    @Transactional(readOnly = true)
    public List<AreaComumResponse> areasAtivas(UUID idCondominio) {
        return areas.findByIdCondominioOrderByNomeAsc(idCondominio).stream().filter(AreaComum::isAtiva)
                .map(ReservaService::paraAreaResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<Horario> ocupacao(UUID idCondominio, UUID idArea, LocalDate de, LocalDate ate) {
        if (ate.isBefore(de) || de.plusDays(120).isBefore(ate)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "Consulte um período de até 120 dias.");
        }
        return reservas.buscarNoPeriodo(idCondominio, idArea, de, ate).stream().filter(r -> r.getSituacao().isAtiva())
                .map(r -> new Horario(r.getIdArea(), r.getData(), r.getHoraInicio(), r.getHoraFim(), r.getSituacao())).toList();
    }

    public ReservaResponse reservarComoMorador(Condominio condominio, ReservaRequest request, UUID idUsuario, String nomeUsuario) {
        Reserva r = reservar(condominio, request, idUsuario, nomeUsuario, true);
        if (r.getSituacao() == Reserva.Situacao.PENDING) {
            notificacoes.notificar(condominio.getIdAdministradora(),
                    notificacoes.usuariosComPermissao(condominio.getIdAdministradora(), condominio.getId(), Permissao.RESERVATION_EDIT),
                    "Reserva aguardando aprovação", descrever(r) + " · " + nomeUsuario, "/reservas", null);
        }
        return paraResponse(r);
    }

    public List<ReservaResponse> dasUnidades(UUID idCondominio, Collection<UUID> idsUnidades) {
        return reservas.buscarDasUnidades(idCondominio, idsUnidades).stream().map(this::paraResponse).toList();
    }

    public ReservaResponse cancelarComoMorador(UUID idCondominio, UUID id, Collection<UUID> idsUnidades, String motivo) {
        Reserva r = reservas.findByIdAndIdCondominio(id, idCondominio).filter(x -> idsUnidades.contains(x.getIdUnidade()))
                .orElseThrow(() -> ApiException.naoEncontrado("Reserva"));
        if (!r.getData().atTime(r.getHoraInicio()).isAfter(agora())) {
            throw ApiException.regraNegocio("RESERVATION_STARTED", "A reserva já começou ou passou e não pode ser cancelada pelo portal.");
        }
        cancelarInterno(r, motivo);
        return paraResponse(r);
    }

    // ------------------------------------------------------------------ regras

    private Reserva reservar(Condominio condominio, ReservaRequest q, UUID idUsuario, String nomeUsuario, boolean peloMorador) {
        // Trava a área: duas reservas simultâneas do mesmo horário são serializadas (o banco também impede sobreposição).
        AreaComum area = areas.travarNoCondominio(q.idArea(), condominio.getId()).orElseThrow(() -> ApiException.naoEncontrado("Área comum"));
        Unidade unidade = unidades.findByIdAndIdCondominio(q.idUnidade(), condominio.getId()).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        if (!area.isAtiva()) {
            throw ApiException.regraNegocio("AREA_INACTIVE", "A área " + area.getNome() + " não está disponível para reservas.");
        }
        if (!unidade.isAtivo()) {
            throw ApiException.regraNegocio("UNIT_INACTIVE", "A unidade está inativa.");
        }
        validarJanela(area, q, peloMorador);
        if (reservas.haSobreposicao(area.getId(), q.data(), q.horaInicio(), q.horaFim())) {
            throw ApiException.conflito("RESERVATION_CONFLICT", "Já existe reserva para " + area.getNome() + " neste horário.");
        }
        if (area.getLimiteMensalPorUnidade() != null) {
            LocalDate primeiroDia = q.data().withDayOfMonth(1);
            long quantidade = reservas.contarAtivasDaUnidade(area.getId(), unidade.getId(), primeiroDia, primeiroDia.plusMonths(1).minusDays(1));
            if (quantidade >= area.getLimiteMensalPorUnidade()) {
                throw ApiException.regraNegocio("RESERVATION_LIMIT", "A unidade já atingiu o limite de " + area.getLimiteMensalPorUnidade()
                        + " reserva(s) por mês nesta área.");
            }
        }
        if (area.isBloqueiaInadimplente() && temCobrancasVencidas(condominio.getId(), unidade.getId())) {
            throw ApiException.regraNegocio("UNIT_DELINQUENT",
                    "A unidade possui cobranças vencidas. Conforme o regulamento desta área, a reserva fica bloqueada até a regularização.");
        }
        Reserva r = reservas.save(new Reserva(area, unidade.getId(), q.data(), q.horaInicio(), q.horaFim(), q.convidados(),
                aparadoOuNulo(q.observacoes()), idUsuario, nomeUsuario, peloMorador));
        if (r.getSituacao() == Reserva.Situacao.CONFIRMED) {
            cobrarTaxa(condominio, r);
        }
        reservas.flush();
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("area", area.getNome());
        detalhes.put("unit", unidade.rotulo());
        detalhes.put("date", q.data().toString());
        detalhes.put("time", q.horaInicio() + "-" + q.horaFim());
        detalhes.put("status", r.getSituacao().name());
        auditoria.registrar(AcaoAuditoria.RESERVATION_CREATED, "Reservation", r.getId(), detalhes);
        return r;
    }

    private void validarJanela(AreaComum area, ReservaRequest q, boolean peloMorador) {
        if (!q.horaFim().isAfter(q.horaInicio())) {
            throw ApiException.requisicaoInvalida("INVALID_TIME", "O horário final deve ser posterior ao inicial.");
        }
        if (q.horaInicio().isBefore(area.getHoraAbertura()) || q.horaFim().isAfter(area.getHoraFechamento())) {
            throw ApiException.regraNegocio("OUTSIDE_HOURS", "A área funciona das " + area.getHoraAbertura() + " às " + area.getHoraFechamento() + ".");
        }
        if (Duration.between(q.horaInicio(), q.horaFim()).toMinutes() > area.getDuracaoMaximaMinutos()) {
            throw ApiException.regraNegocio("DURATION_EXCEEDED", "Duração máxima de " + area.getDuracaoMaximaMinutos() + " minutos.");
        }
        LocalDateTime inicio = q.data().atTime(q.horaInicio());
        LocalDateTime agora = agora();
        if (!inicio.isAfter(agora)) {
            throw ApiException.regraNegocio("RESERVATION_IN_PAST", "Não é possível reservar horários passados.");
        }
        if (peloMorador && inicio.isBefore(agora.plusHours(area.getAntecedenciaMinimaHoras()))) {
            throw ApiException.regraNegocio("ADVANCE_REQUIRED", "Reservas desta área exigem antecedência mínima de "
                    + area.getAntecedenciaMinimaHoras() + " hora(s).");
        }
        if (q.data().isAfter(hoje().plusDays(area.getAntecedenciaMaximaDias()))) {
            throw ApiException.regraNegocio("TOO_FAR_AHEAD", "Reservas abertas com até " + area.getAntecedenciaMaximaDias() + " dias de antecedência.");
        }
    }

    private boolean temCobrancasVencidas(UUID idCondominio, UUID idUnidade) {
        LocalDate hoje = hoje();
        return lancamentos.buscarRecebiveisDaUnidade(idCondominio, idUnidade).stream().anyMatch(e -> e.isVencido(hoje));
    }

    /** Gera a cobrança da taxa de uso na unidade (título a receber, com multa/juros do condomínio). */
    private void cobrarTaxa(Condominio condominio, Reserva r) {
        if (r.getTaxa().signum() <= 0 || r.getIdLancamento() != null) {
            return;
        }
        LocalDate vencimento = r.getData().isBefore(hoje()) ? hoje() : r.getData();
        bloqueioPeriodo.exigirAberto(condominio.getId(), vencimento.withDayOfMonth(1));
        ContaPlano conta = contasPlano.buscarDoSistemaPorCodigo(CODIGO_CONTA_TAXA_AREA).orElseThrow();
        Unidade unidade = unidades.findById(r.getIdUnidade()).orElseThrow();
        Pessoa responsavel = vinculos.buscarResponsavelFinanceiroVigente(unidade.getId()).map(Vinculo::getPessoa)
                .or(() -> vinculos.buscarVigentesPorUnidades(List.of(unidade.getId())).stream()
                        .filter(o -> o.getTipo() == Vinculo.Tipo.OWNER).map(Vinculo::getPessoa).findFirst())
                .orElse(null);
        UUID por = acesso.usuario().idUsuario();
        Lancamento lancamento = new Lancamento(condominio.getIdAdministradora(), condominio.getId(), Lancamento.Natureza.INCOME,
                Lancamento.Origem.MANUAL, por);
        lancamento.atualizar("Taxa de uso — " + nomeArea(r) + " em " + r.getData().format(BR), conta, null, null, unidade, responsavel,
                r.getTaxa(), vencimento, vencimento, null, "Reserva " + r.getId(), por);
        lancamento.definirEncargos(condominio.getPercentualMulta(), condominio.getPercentualJurosMensal());
        lancamentos.save(lancamento);
        r.vincularCobranca(lancamento.getId());
    }

    private void cancelarInterno(Reserva r, String motivo) {
        Instant agora = Instant.now(clock);
        r.cancelar(motivo.trim(), agora);
        if (r.getIdLancamento() != null) {
            lancamentos.findById(r.getIdLancamento()).filter(e -> e.getSituacao() == Lancamento.Situacao.OPEN).ifPresent(e -> {
                bloqueioPeriodo.exigirAberto(r.getIdCondominio(), e.getCompetencia());
                e.cancelar("Reserva cancelada: " + motivo.trim(), acesso.usuario().idUsuario(), agora);
            });
        }
        reservas.flush();
        auditoria.registrar(AcaoAuditoria.RESERVATION_CANCELLED, "Reservation", r.getId(), Map.of("reason", motivo.trim()));
    }

    private Reserva carregar(UUID idCondominio, UUID id) {
        return reservas.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Reserva"));
    }

    private String nomeArea(Reserva r) {
        return areas.findById(r.getIdArea()).map(AreaComum::getNome).orElse("área comum");
    }

    private String descrever(Reserva r) {
        return nomeArea(r) + " em " + r.getData().format(BR) + ", " + r.getHoraInicio() + "–" + r.getHoraFim();
    }

    private ReservaResponse paraResponse(Reserva r) {
        return new ReservaResponse(r.getId(), r.getIdArea(), nomeArea(r), r.getIdUnidade(),
                unidades.findById(r.getIdUnidade()).map(Unidade::rotulo).orElse(null), r.getData(), r.getHoraInicio(), r.getHoraFim(),
                r.getConvidados(), r.getObservacoes(), r.getSituacao(), r.getTaxa(), r.getIdLancamento(), r.getNomeSolicitante(), r.isPeloMorador(),
                r.getObservacaoDecisao(), r.getMotivoCancelamento(), r.getCriadoEm());
    }

    private static AreaComumResponse paraAreaResponse(AreaComum a) {
        return new AreaComumResponse(a.getId(), a.getNome(), a.getDescricao(), a.getRegras(), a.getCapacidade(), a.getTaxa(),
                a.isExigeAprovacao(), a.isBloqueiaInadimplente(), a.getHoraAbertura(), a.getHoraFechamento(), a.getDuracaoMaximaMinutos(),
                a.getAntecedenciaMinimaHoras(), a.getAntecedenciaMaximaDias(), a.getLimiteMensalPorUnidade(), a.isAtiva(), a.getVersao());
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock.withZone(FUSO));
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO));
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
