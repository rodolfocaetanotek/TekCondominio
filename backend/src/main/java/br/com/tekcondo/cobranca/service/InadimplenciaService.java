package br.com.tekcondo.cobranca.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.AcordoRequest;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.AcordoResponse;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.DebitosUnidade;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.Inadimplencia;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.LinhaDebito;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.LinhaInadimplencia;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.NotaCobrancaRequest;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.NotaCobrancaResponse;
import br.com.tekcondo.cobranca.entity.Acordo;
import br.com.tekcondo.cobranca.entity.NotaCobranca;
import br.com.tekcondo.cobranca.repository.AcordoRepository;
import br.com.tekcondo.cobranca.repository.NotaCobrancaRepository;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.ContaPlanoRepository;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.financeiro.service.LancamentoService;
import br.com.tekcondo.financeiro.service.EncargosCalculator;
import br.com.tekcondo.financeiro.service.BloqueioPeriodo;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inadimplência, demonstrativo de débitos, histórico de cobrança e acordos de parcelamento.
 * Inadimplência = títulos a receber de unidades, em aberto e vencidos na data de referência.
 */
@Service
public class InadimplenciaService {

    private static final String CODIGO_CONTA_ACORDO = "1.07";
    private static final BigDecimal CEM = new BigDecimal("100");

    private final LancamentoRepository lancamentoRepository;
    private final AcordoRepository acordoRepository;
    private final NotaCobrancaRepository notaRepository;
    private final ContaPlanoRepository contaPlanoRepository;
    private final UnidadeRepository unidadeRepository;
    private final VinculoRepository vinculoRepository;
    private final LancamentoService lancamentoService;
    private final BloqueioPeriodo bloqueioPeriodo;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public InadimplenciaService(LancamentoRepository lancamentoRepository, AcordoRepository acordoRepository,
                                NotaCobrancaRepository notaRepository, ContaPlanoRepository contaPlanoRepository,
                                UnidadeRepository unidadeRepository, VinculoRepository vinculoRepository,
                                LancamentoService lancamentoService, BloqueioPeriodo bloqueioPeriodo, AcessoCondominio acesso,
                                AuditoriaService auditoria, Clock clock) {
        this.lancamentoRepository = lancamentoRepository;
        this.acordoRepository = acordoRepository;
        this.notaRepository = notaRepository;
        this.contaPlanoRepository = contaPlanoRepository;
        this.unidadeRepository = unidadeRepository;
        this.vinculoRepository = vinculoRepository;
        this.lancamentoService = lancamentoService;
        this.bloqueioPeriodo = bloqueioPeriodo;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- inadimplência

    @Transactional(readOnly = true)
    public Inadimplencia inadimplencia(UUID idCondominio, LocalDate dataBaseParam) {
        acesso.exigir(idCondominio, Permissao.BILLING_VIEW);
        LocalDate dataBase = dataBaseParam == null ? LocalDate.now(clock) : dataBaseParam;
        List<Lancamento> vencidos = lancamentoRepository.findAll(recebiveisVencidos(idCondominio, dataBase));
        Map<UUID, List<Lancamento>> porUnidade = vencidos.stream().collect(Collectors.groupingBy(e -> e.getUnidade().getId(),
                LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Pessoa> responsaveis = responsaveis(porUnidade.keySet());
        List<LinhaInadimplencia> linhas = new ArrayList<>();
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalEncargos = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<Lancamento>> e : porUnidade.entrySet()) {
            BigDecimal principal = BigDecimal.ZERO;
            BigDecimal encargos = BigDecimal.ZERO;
            LocalDate maisAntigo = null;
            for (Lancamento lancamento : e.getValue()) {
                EncargosCalculator.Calculo c = calcular(lancamento, dataBase);
                principal = principal.add(c.principal());
                encargos = encargos.add(c.multa()).add(c.juros());
                maisAntigo = maisAntigo == null || lancamento.getDataVencimento().isBefore(maisAntigo) ? lancamento.getDataVencimento() : maisAntigo;
            }
            Unidade unidade = e.getValue().getFirst().getUnidade();
            Pessoa p = responsaveis.get(e.getKey());
            LocalDate ultimoContato = notaRepository.findByIdCondominioAndIdUnidadeOrderByDataContatoDescCriadoEmDesc(idCondominio, e.getKey())
                    .stream().findFirst().map(NotaCobranca::getDataContato).orElse(null);
            linhas.add(new LinhaInadimplencia(e.getKey(), unidade.rotulo(), p == null ? null : p.getNome(), p == null ? null : p.getEmail(),
                    p == null ? null : p.getTelefone(), e.getValue().size(), principal, encargos, principal.add(encargos), maisAntigo,
                    java.time.temporal.ChronoUnit.DAYS.between(maisAntigo, dataBase), ultimoContato));
            totalPrincipal = totalPrincipal.add(principal);
            totalEncargos = totalEncargos.add(encargos);
        }
        linhas.sort(Comparator.comparing(LinhaInadimplencia::total).reversed());
        BigDecimal emitido = lancamentoRepository.findAll(recebiveisEmitidos(idCondominio, dataBase.minusMonths(12), dataBase)).stream()
                .map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal indice = emitido.signum() == 0 ? BigDecimal.ZERO
                : totalPrincipal.multiply(CEM).divide(emitido, 2, RoundingMode.HALF_UP);
        return new Inadimplencia(dataBase, totalPrincipal, totalEncargos, totalPrincipal.add(totalEncargos), emitido, indice,
                linhas.size(), unidadeRepository.countByIdCondominioAndAtivoTrue(idCondominio), linhas);
    }

    /** Demonstrativo de débitos da unidade, com valores atualizados (multa e juros) até a data. */
    @Transactional(readOnly = true)
    public DebitosUnidade debitosUnidade(UUID idCondominio, UUID idUnidade, LocalDate dataBaseParam) {
        acesso.exigir(idCondominio, Permissao.BILLING_VIEW);
        Unidade unidade = unidadeRepository.findByIdAndIdCondominio(idUnidade, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        return montarDebitosUnidade(idCondominio, unidade, dataBaseParam == null ? LocalDate.now(clock) : dataBaseParam);
    }

    public DebitosUnidade montarDebitosUnidade(UUID idCondominio, Unidade unidade, LocalDate dataBase) {
        List<LinhaDebito> linhas = new ArrayList<>();
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal encargos = BigDecimal.ZERO;
        for (Lancamento e : lancamentoRepository.buscarRecebiveisDaUnidade(idCondominio, unidade.getId())) {
            if (e.getSituacao() == Lancamento.Situacao.CANCELLED) {
                continue;
            }
            boolean aberto = e.getSituacao() == Lancamento.Situacao.OPEN || e.getSituacao() == Lancamento.Situacao.PARTIAL;
            EncargosCalculator.Calculo c = aberto ? calcular(e, dataBase)
                    : new EncargosCalculator.Calculo(BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            linhas.add(new LinhaDebito(e.getId(), e.getDescricao(), e.getCompetencia(), e.getDataVencimento(), e.getValor(),
                    e.getValorPago(), aberto ? e.valorAberto() : BigDecimal.ZERO, e.getSituacao(), c.diasAtraso(), c.multa(),
                    c.juros(), c.total()));
            if (aberto) {
                principal = principal.add(c.principal());
                encargos = encargos.add(c.multa()).add(c.juros());
            }
        }
        Pessoa p = responsaveis(Set.of(unidade.getId())).get(unidade.getId());
        List<NotaCobrancaResponse> historico = notaRepository.findByIdCondominioAndIdUnidadeOrderByDataContatoDescCriadoEmDesc(idCondominio, unidade.getId())
                .stream().map(NotaCobrancaResponse::de).toList();
        List<AcordoResponse> acordosDaUnidade = acordoRepository.findByIdCondominioOrderByCriadoEmDesc(idCondominio).stream()
                .filter(a -> a.getIdUnidade().equals(unidade.getId())).map(a -> paraResponse(a, unidade)).toList();
        return new DebitosUnidade(unidade.getId(), unidade.rotulo(), p == null ? null : p.getNome(), dataBase, principal, encargos,
                principal.add(encargos), linhas, historico, acordosDaUnidade);
    }

    @Transactional
    public NotaCobrancaResponse registrarNota(UUID idCondominio, UUID idUnidade, NotaCobrancaRequest request) {
        Condominio condominio = acesso.exigir(idCondominio, Permissao.BILLING_EDIT);
        unidadeRepository.findByIdAndIdCondominio(idUnidade, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        if (request.idLancamento() != null) {
            Lancamento e = lancamentoService.carregar(idCondominio, request.idLancamento());
            if (e.getUnidade() == null || !e.getUnidade().getId().equals(idUnidade)) {
                throw ApiException.regraNegocio("ENTRY_NOT_OF_UNIT", "A cobrança informada não pertence a esta unidade.");
            }
        }
        var usuario = acesso.usuario();
        NotaCobranca nota = notaRepository.save(new NotaCobranca(condominio.getIdAdministradora(), idCondominio, idUnidade,
                request.idLancamento(), request.tipoContato(), request.dataContato(), request.observacao().trim(), usuario.idUsuario(), usuario.nome()));
        auditoria.registrar(AcaoAuditoria.COLLECTION_NOTE_ADDED, "Unit", idUnidade, Map.of("type", request.tipoContato().name()));
        return NotaCobrancaResponse.de(nota);
    }

    // ---------------------------------------------------------------- acordos

    @Transactional(readOnly = true)
    public List<AcordoResponse> acordos(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.BILLING_VIEW);
        return acordoRepository.findByIdCondominioOrderByCriadoEmDesc(idCondominio).stream()
                .map(a -> paraResponse(a, unidadeRepository.findById(a.getIdUnidade()).orElse(null))).toList();
    }

    @Transactional
    public AcordoResponse cadastrarAcordo(UUID idCondominio, AcordoRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.BILLING_EDIT);
        Unidade unidade = unidadeRepository.findByIdAndIdCondominio(request.idUnidade(), idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        LocalDate hoje = LocalDate.now(clock);
        if (request.dataPrimeiroVencimento().isBefore(hoje)) {
            throw ApiException.regraNegocio("FIRST_DUE_IN_PAST", "O vencimento da 1ª parcela não pode estar no passado.");
        }
        List<Lancamento> originais = new ArrayList<>();
        for (UUID id : new HashSet<>(request.idsLancamentos())) {
            Lancamento e = lancamentoService.carregar(idCondominio, id);
            if (e.getNatureza() != Lancamento.Natureza.INCOME || e.getUnidade() == null || !e.getUnidade().getId().equals(unidade.getId())) {
                throw ApiException.regraNegocio("ENTRY_NOT_OF_UNIT", "Selecione apenas cobranças desta unidade.");
            }
            if (e.getSituacao() != Lancamento.Situacao.OPEN && e.getSituacao() != Lancamento.Situacao.PARTIAL) {
                throw ApiException.regraNegocio("ENTRY_NOT_OPEN", "A cobrança \"" + e.getDescricao() + "\" não está em aberto.");
            }
            originais.add(e);
        }
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal encargos = BigDecimal.ZERO;
        for (Lancamento e : originais) {
            EncargosCalculator.Calculo c = calcular(e, hoje);
            principal = principal.add(c.principal());
            if (request.incluirEncargos()) {
                encargos = encargos.add(c.multa()).add(c.juros());
            }
        }
        BigDecimal desconto = request.desconto() == null ? BigDecimal.ZERO : request.desconto();
        Pessoa responsavel = responsaveis(Set.of(unidade.getId())).get(unidade.getId());
        UUID por = acesso.usuario().idUsuario();
        Acordo acordo = acordoRepository.save(new Acordo(condominio.getIdAdministradora(), idCondominio, unidade.getId(),
                responsavel == null ? null : responsavel.getId(), principal, encargos, desconto, request.totalParcelas(),
                request.dataPrimeiroVencimento(), aparadoOuNulo(request.observacoes()), por));
        originais.forEach(e -> {
            e.renegociar(acordo.getId(), por);
            acordoRepository.vincularOriginal(acordo.getId(), e.getId());
        });

        ContaPlano conta = contaPlanoRepository.buscarDoSistemaPorCodigo(CODIGO_CONTA_ACORDO).orElseThrow();
        BigDecimal parcela = acordo.getValorTotal().divide(BigDecimal.valueOf(request.totalParcelas()), 2, RoundingMode.DOWN);
        BigDecimal ultima = acordo.getValorTotal().subtract(parcela.multiply(BigDecimal.valueOf(request.totalParcelas() - 1L)));
        String ref = acordo.getId().toString().substring(0, 8).toUpperCase();
        for (int i = 0; i < request.totalParcelas(); i++) {
            Lancamento e = new Lancamento(condominio.getIdAdministradora(), idCondominio, Lancamento.Natureza.INCOME,
                    Lancamento.Origem.AGREEMENT, por);
            LocalDate vencimento = request.dataPrimeiroVencimento().plusMonths(i);
            e.atualizar("Acordo " + ref + " — parcela " + (i + 1) + "/" + request.totalParcelas(), conta, null, null, unidade,
                    responsavel, i == request.totalParcelas() - 1 ? ultima : parcela, vencimento, vencimento, null, null, por);
            e.definirEncargos(condominio.getPercentualMulta(), condominio.getPercentualJurosMensal());
            e.vincularAcordo(acordo.getId());
            e.recorrencia(acordo.getId(), i + 1, request.totalParcelas());
            lancamentoRepository.save(e);
        }
        lancamentoRepository.flush();
        auditoria.registrar(AcaoAuditoria.AGREEMENT_CREATED, "Agreement", acordo.getId(), Map.of("unit", unidade.rotulo(),
                "original", principal.toPlainString(), "charges", encargos.toPlainString(), "discount", desconto.toPlainString(),
                "total", acordo.getValorTotal().toPlainString(), "installments", request.totalParcelas()));
        return paraResponse(acordo, unidade);
    }

    /** Cancela acordo sem parcelas pagas: as parcelas são canceladas e os débitos originais voltam a ficar em aberto. */
    @Transactional
    public AcordoResponse cancelarAcordo(UUID idCondominio, UUID id, String motivo) {
        acesso.exigir(idCondominio, Permissao.BILLING_EDIT);
        Acordo acordo = acordoRepository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Acordo"));
        List<Lancamento> parcelas = lancamentoRepository.findByIdAcordo(id);
        if (parcelas.stream().anyMatch(e -> e.getValorPago().signum() > 0)) {
            throw ApiException.regraNegocio("AGREEMENT_HAS_PAYMENTS",
                    "O acordo possui parcelas pagas e não pode ser cancelado. Registre o descumprimento no histórico de cobrança.");
        }
        bloqueioPeriodo.exigirAberto(idCondominio, parcelas.stream().map(Lancamento::getCompetencia).toArray(LocalDate[]::new));
        UUID por = acesso.usuario().idUsuario();
        Instant agora = Instant.now(clock);
        acordo.cancelar(motivo.trim(), por, agora);
        parcelas.forEach(e -> e.cancelar("Acordo cancelado: " + motivo.trim(), por, agora));
        acordoRepository.idsLancamentosOriginais(id).forEach(idLancamento -> lancamentoRepository.findById(idLancamento)
                .ifPresent(e -> e.restaurarDoAcordo(por)));
        lancamentoRepository.flush();
        auditoria.registrar(AcaoAuditoria.AGREEMENT_CANCELLED, "Agreement", id, Map.of("reason", motivo.trim()));
        return paraResponse(acordo, unidadeRepository.findById(acordo.getIdUnidade()).orElse(null));
    }

    // ---------------------------------------------------------------- apoio

    private EncargosCalculator.Calculo calcular(Lancamento e, LocalDate dataBase) {
        return EncargosCalculator.calcular(e.valorAberto(), e.getDataVencimento(), dataBase, e.getPercentualMulta(), e.getPercentualJurosMensal());
    }

    private static Specification<Lancamento> recebiveisVencidos(UUID idCondominio, LocalDate dataBase) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("unidade").fetch("bloco", JoinType.LEFT);
            }
            return cb.and(cb.equal(root.get("idCondominio"), idCondominio),
                    cb.equal(root.get("natureza"), Lancamento.Natureza.INCOME),
                    cb.isNotNull(root.get("unidade")),
                    root.get("situacao").in(Lancamento.Situacao.OPEN, Lancamento.Situacao.PARTIAL),
                    cb.lessThan(root.get("dataVencimento"), dataBase));
        };
    }

    private static Specification<Lancamento> recebiveisEmitidos(UUID idCondominio, LocalDate de, LocalDate ate) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("idCondominio"), idCondominio),
                cb.equal(root.get("natureza"), Lancamento.Natureza.INCOME),
                cb.isNotNull(root.get("unidade")),
                cb.notEqual(root.get("situacao"), Lancamento.Situacao.CANCELLED),
                cb.notEqual(root.get("origem"), Lancamento.Origem.AGREEMENT),
                cb.between(root.get("dataVencimento"), de, ate));
    }

    private Map<UUID, Pessoa> responsaveis(Set<UUID> idsUnidades) {
        if (idsUnidades.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Pessoa> resultado = new java.util.HashMap<>();
        vinculoRepository.buscarVigentesPorUnidades(idsUnidades).stream()
                .collect(Collectors.groupingBy(o -> o.getUnidade().getId()))
                .forEach((idUnidade, vinculos) -> vinculos.stream().filter(Vinculo::isResponsavelFinanceiro).findFirst()
                        .or(() -> vinculos.stream().filter(o -> o.getTipo() == Vinculo.Tipo.OWNER).findFirst())
                        .ifPresent(o -> resultado.put(idUnidade, o.getPessoa())));
        return resultado;
    }

    private AcordoResponse paraResponse(Acordo a, Unidade unidade) {
        LocalDate hoje = LocalDate.now(clock);
        List<Lancamento> parcelas = lancamentoRepository.findByIdAcordo(a.getId());
        long pagas = parcelas.stream().filter(e -> e.getSituacao() == Lancamento.Situacao.PAID).count();
        Acordo.Situacao situacao = a.getSituacao() == Acordo.Situacao.ACTIVE && !parcelas.isEmpty() && pagas == parcelas.size()
                ? Acordo.Situacao.COMPLETED : a.getSituacao();
        return new AcordoResponse(a.getId(), a.getIdUnidade(), unidade == null ? null : unidade.rotulo(), a.getValorOriginal(),
                a.getValorEncargos(), a.getDesconto(), a.getValorTotal(), a.getTotalParcelas(), a.getDataPrimeiroVencimento(), situacao,
                pagas, a.getObservacoes(), a.getMotivoCancelamento(), a.getCriadoEm(),
                parcelas.stream().sorted(Comparator.comparing(Lancamento::getDataVencimento))
                        .map(e -> lancamentoService.resumo(e, hoje)).toList());
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
