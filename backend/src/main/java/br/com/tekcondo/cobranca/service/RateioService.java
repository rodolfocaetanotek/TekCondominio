package br.com.tekcondo.cobranca.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.LinhaRateio;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.RateioRequest;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.RateioResponse;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.SimulacaoRateio;
import br.com.tekcondo.cobranca.entity.Rateio;
import br.com.tekcondo.cobranca.repository.RateioRepository;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.ContaPlanoRepository;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.financeiro.service.ConfiguracaoFinanceiraService;
import br.com.tekcondo.financeiro.service.BloqueioPeriodo;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rateio e geração das cobranças mensais (um título a receber por unidade). */
@Service
public class RateioService {

    private final RateioRepository rateioRepository;
    private final LancamentoRepository lancamentoRepository;
    private final ContaPlanoRepository contaPlanoRepository;
    private final UnidadeRepository unidadeRepository;
    private final VinculoRepository vinculoRepository;
    private final ConfiguracaoFinanceiraService configuracao;
    private final BloqueioPeriodo bloqueioPeriodo;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public RateioService(RateioRepository rateioRepository, LancamentoRepository lancamentoRepository,
                         ContaPlanoRepository contaPlanoRepository, UnidadeRepository unidadeRepository, VinculoRepository vinculoRepository,
                         ConfiguracaoFinanceiraService configuracao, BloqueioPeriodo bloqueioPeriodo, AcessoCondominio acesso,
                         AuditoriaService auditoria, Clock clock) {
        this.rateioRepository = rateioRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.contaPlanoRepository = contaPlanoRepository;
        this.unidadeRepository = unidadeRepository;
        this.vinculoRepository = vinculoRepository;
        this.configuracao = configuracao;
        this.bloqueioPeriodo = bloqueioPeriodo;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    private record Plano(Condominio condominio, ContaPlano conta, LocalDate competencia, LocalDate dataVencimento,
                         List<Unidade> unidades, Map<UUID, BigDecimal> cotas, Map<UUID, Pessoa> responsaveis, BigDecimal total) {
    }

    @Transactional(readOnly = true)
    public SimulacaoRateio simular(UUID idCondominio, RateioRequest request) {
        return paraSimulacao(planejar(idCondominio, request));
    }

    @Transactional
    public RateioResponse gerar(UUID idCondominio, RateioRequest request) {
        Plano plano = planejar(idCondominio, request);
        bloqueioPeriodo.exigirAberto(idCondominio, plano.competencia());
        UUID idUsuario = acesso.usuario().idUsuario();
        Rateio rateio = rateioRepository.save(new Rateio(plano.condominio().getIdAdministradora(), idCondominio,
                plano.competencia(), request.descricao().trim(), plano.conta().getId(), request.criterio(),
                request.criterio() == RateioCalculator.Criterio.CONSUMPTION ? plano.total() : request.valor(),
                plano.dataVencimento(), plano.cotas().size(), plano.total(), idUsuario));
        for (Unidade unidade : plano.unidades()) {
            BigDecimal valor = plano.cotas().get(unidade.getId());
            if (valor == null || valor.signum() <= 0) {
                continue;
            }
            Lancamento cobranca = new Lancamento(plano.condominio().getIdAdministradora(), idCondominio,
                    Lancamento.Natureza.INCOME, Lancamento.Origem.ALLOCATION, idUsuario);
            cobranca.atualizar(request.descricao().trim(), plano.conta(), null, null, unidade, plano.responsaveis().get(unidade.getId()),
                    valor, plano.dataVencimento(), plano.competencia(), null, null, idUsuario);
            cobranca.definirEncargos(plano.condominio().getPercentualMulta(), plano.condominio().getPercentualJurosMensal());
            cobranca.vincularRateio(rateio.getId());
            lancamentoRepository.save(cobranca);
        }
        auditoria.registrar(AcaoAuditoria.ALLOCATION_GENERATED, "Allocation", rateio.getId(), Map.of(
                "competence", plano.competencia().toString(), "method", request.criterio().name(),
                "units", plano.cotas().size(), "total", plano.total().toPlainString()));
        return paraResponse(rateio, plano.conta());
    }

    @Transactional(readOnly = true)
    public List<RateioResponse> listar(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.BILLING_VIEW);
        Map<UUID, ContaPlano> contas = contaPlanoRepository.buscarDisponiveis(acesso.idAdministradora()).stream()
                .collect(Collectors.toMap(ContaPlano::getId, a -> a));
        return rateioRepository.findByIdCondominioOrderByCompetenciaDescCriadoEmDesc(idCondominio).stream()
                .map(a -> paraResponse(a, contas.get(a.getIdContaPlano()))).toList();
    }

    /** Cancela o rateio e suas cobranças — somente se nenhuma tiver sido paga. */
    @Transactional
    public RateioResponse cancelar(UUID idCondominio, UUID id, String motivo) {
        acesso.exigir(idCondominio, Permissao.BILLING_EDIT);
        acesso.exigir(idCondominio, Permissao.FINANCE_CANCEL);
        Rateio rateio = rateioRepository.findByIdAndIdCondominio(id, idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Rateio"));
        bloqueioPeriodo.exigirAberto(idCondominio, rateio.getCompetencia());
        List<Lancamento> cobrancas = lancamentoRepository.findByIdRateio(id);
        if (cobrancas.stream().anyMatch(e -> e.getValorPago().signum() > 0)) {
            throw ApiException.regraNegocio("ALLOCATION_HAS_PAYMENTS",
                    "Há cobranças deste rateio com pagamento. Estorne as baixas ou cancele as cobranças individualmente.");
        }
        UUID por = acesso.usuario().idUsuario();
        Instant agora = Instant.now(clock);
        cobrancas.stream().filter(e -> e.getSituacao() == Lancamento.Situacao.OPEN)
                .forEach(e -> e.cancelar("Rateio cancelado: " + motivo.trim(), por, agora));
        rateio.cancelar(motivo.trim(), por, agora);
        auditoria.registrar(AcaoAuditoria.ALLOCATION_CANCELLED, "Allocation", id, Map.of("reason", motivo.trim()));
        return paraResponse(rateio, contaPlanoRepository.findById(rateio.getIdContaPlano()).orElse(null));
    }

    // ------------------------------------------------------------------

    private Plano planejar(UUID idCondominio, RateioRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.BILLING_EDIT);
        ContaPlano conta = configuracao.contaAnalitica(request.idContaPlano(), ContaPlano.Natureza.INCOME);
        LocalDate competencia = YearMonth.parse(request.competencia()).atDay(1);
        if (rateioRepository.existeGerado(idCondominio, competencia, conta.getId())) {
            throw ApiException.conflito("ALLOCATION_EXISTS", "Já existe cobrança gerada para " + conta.getNome()
                    + " na competência " + YearMonth.from(competencia) + ". Cancele a anterior para gerar novamente.");
        }
        if (request.criterio() != RateioCalculator.Criterio.CONSUMPTION && request.valor() == null) {
            throw ApiException.requisicaoInvalida("AMOUNT_REQUIRED", "Informe o valor a ratear.");
        }
        LocalDate dataVencimento = request.dataVencimento() != null ? request.dataVencimento()
                : competencia.withDayOfMonth(condominio.getDiaVencimento());
        List<Unidade> ativas = unidadeRepository.buscarAtivasDoCondominio(idCondominio).stream()
                .sorted(Comparator.comparing(Unidade::rotulo)).toList();
        List<RateioCalculator.Cota> cotas = ativas.stream()
                .map(u -> new RateioCalculator.Cota(u.getId(), u.getFracaoIdeal())).toList();
        Map<UUID, BigDecimal> resultado = RateioCalculator.ratear(request.criterio(), request.valor(), cotas,
                request.consumo());
        BigDecimal total = resultado.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Plano(condominio, conta, competencia, dataVencimento, ativas, resultado, responsaveis(ativas), total);
    }

    /** Responsável financeiro vigente; na falta, o primeiro proprietário. */
    private Map<UUID, Pessoa> responsaveis(List<Unidade> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Vinculo>> porUnidade = vinculoRepository.buscarVigentesPorUnidades(lista.stream().map(Unidade::getId).toList()).stream()
                .collect(Collectors.groupingBy(o -> o.getUnidade().getId()));
        Map<UUID, Pessoa> resultado = new java.util.HashMap<>();
        porUnidade.forEach((idUnidade, vinculos) -> vinculos.stream().filter(Vinculo::isResponsavelFinanceiro).findFirst()
                .or(() -> vinculos.stream().filter(o -> o.getTipo() == Vinculo.Tipo.OWNER).findFirst())
                .ifPresent(o -> resultado.put(idUnidade, o.getPessoa())));
        return resultado;
    }

    private SimulacaoRateio paraSimulacao(Plano plano) {
        List<LinhaRateio> linhas = new ArrayList<>();
        int semResponsavel = 0;
        for (Unidade u : plano.unidades()) {
            BigDecimal valor = plano.cotas().get(u.getId());
            if (valor == null) {
                continue;
            }
            Pessoa p = plano.responsaveis().get(u.getId());
            if (p == null) {
                semResponsavel++;
            }
            linhas.add(new LinhaRateio(u.getId(), u.rotulo(), u.getFracaoIdeal(), p == null ? null : p.getNome(), valor));
        }
        return new SimulacaoRateio(plano.competencia(), plano.dataVencimento(), plano.total(), linhas.size(), semResponsavel, linhas);
    }

    private RateioResponse paraResponse(Rateio a, ContaPlano conta) {
        List<Lancamento> cobrancas = lancamentoRepository.findByIdRateio(a.getId());
        long pagas = cobrancas.stream().filter(e -> e.getSituacao() == Lancamento.Situacao.PAID).count();
        BigDecimal recebido = cobrancas.stream().map(Lancamento::getValorPago).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RateioResponse(a.getId(), a.getCompetencia(), a.getDescricao(),
                conta == null ? null : conta.getCodigo(), conta == null ? null : conta.getNome(), a.getCriterio(),
                a.getValorBase(), a.getDataVencimento(), a.getQuantidadeUnidades(), a.getValorGerado(), a.getSituacao(), pagas, recebido,
                a.getMotivoCancelamento(), a.getCriadoEm());
    }
}
