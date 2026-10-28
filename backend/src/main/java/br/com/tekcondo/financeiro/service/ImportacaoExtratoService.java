package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.BaixaRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoDetalhe;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoRequest;
import br.com.tekcondo.financeiro.entity.Baixa;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.ImportacaoExtrato;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.entity.LinhaExtrato;
import br.com.tekcondo.financeiro.repository.BaixaRepository;
import br.com.tekcondo.financeiro.repository.ImportacaoExtratoRepository;
import br.com.tekcondo.financeiro.repository.LinhaExtratoRepository;
import br.com.tekcondo.financeiro.repository.TransferenciaRepository;
import br.com.tekcondo.financeiro.service.ConciliacaoExtratoMatcher.Candidato;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.banco.OfxParser;
import br.com.tekcondo.comum.exception.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação de extratos OFX e conciliação linha a linha: casamento automático inequívoco, casamento manual,
 * "ignorar" com justificativa e lançamento direto (tarifas, rendimentos) a partir da linha do extrato.
 */
@Service
public class ImportacaoExtratoService {

    /** Janela de datas aceita no casamento manual (compensação de cheques, crédito D+n etc.). */
    private static final int TOLERANCIA_MANUAL_DIAS = 31;

    public record ResultadoImportacao(UUID idImportacao, String nomeArquivo, LocalDate inicioPeriodo, LocalDate fimPeriodo,
                                      int linhasArquivo, int linhasNovas, int duplicadas, int conciliadasAutomaticamente,
                                      int pendentes) {
    }

    public record Sugestao(String tipo, UUID id, LocalDate data, BigDecimal valor, String descricao) {
    }

    public record LinhaExtratoResponse(UUID id, String fitId, LocalDate dataLancamento, BigDecimal valor, String tipoTransacao,
                                       String historico, LinhaExtrato.Situacao situacao, String tipoConciliado,
                                       UUID idConciliado, String observacao, List<Sugestao> sugestoes) {
    }

    public record ResumoImportacao(UUID id, String nomeArquivo, LocalDate inicioPeriodo, LocalDate fimPeriodo, int totalLinhas,
                                   int linhasNovas, int conciliadasAutomaticamente, Instant criadoEm, String nomeCriadoPor) {
    }

    public record ConciliarRequest(@NotBlank(message = "Informe o tipo da movimentação.") String tipo,
                                   @NotNull(message = "Informe a movimentação.") UUID idMovimentacao) {
    }

    public record IgnorarRequest(@NotBlank(message = "Informe o motivo.") @Size(min = 3, max = 255) String observacao) {
    }

    public record LancarRequest(@NotNull(message = "Informe a conta do plano de contas.") UUID idContaPlano,
                                @NotBlank(message = "Informe a descrição.") @Size(max = 200) String descricao,
                                UUID idCentroCusto) {
    }

    private final ImportacaoExtratoRepository importacaoRepository;
    private final LinhaExtratoRepository linhaRepository;
    private final BaixaRepository baixaRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final ConfiguracaoFinanceiraService configuracao;
    private final ConsultasFinanceiras consultas;
    private final LancamentoService lancamentoService;
    private final BloqueioPeriodo bloqueioPeriodo;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public ImportacaoExtratoService(ImportacaoExtratoRepository importacaoRepository, LinhaExtratoRepository linhaRepository,
                                    BaixaRepository baixaRepository, TransferenciaRepository transferenciaRepository,
                                    ConfiguracaoFinanceiraService configuracao, ConsultasFinanceiras consultas,
                                    LancamentoService lancamentoService, BloqueioPeriodo bloqueioPeriodo, AcessoCondominio acesso,
                                    AuditoriaService auditoriaService, Clock clock) {
        this.importacaoRepository = importacaoRepository;
        this.linhaRepository = linhaRepository;
        this.baixaRepository = baixaRepository;
        this.transferenciaRepository = transferenciaRepository;
        this.configuracao = configuracao;
        this.consultas = consultas;
        this.lancamentoService = lancamentoService;
        this.bloqueioPeriodo = bloqueioPeriodo;
        this.acesso = acesso;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional
    public ResultadoImportacao importarOfx(UUID idCondominio, UUID idConta, String nomeArquivo, byte[] conteudo,
                                           boolean ignorarConferenciaConta) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        ContaBancaria conta = configuracao.carregarContaBancaria(idCondominio, idConta);
        if (!conta.isAtivo()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta bancária está inativa.");
        }
        String hash = OfxParser.sha256(conteudo);
        if (importacaoRepository.existsByIdContaBancariaAndHashArquivo(idConta, hash)) {
            throw ApiException.conflito("STATEMENT_ALREADY_IMPORTED", "Este arquivo de extrato já foi importado nesta conta.");
        }
        OfxParser.Extrato extrato = OfxParser.parse(conteudo);
        if (extrato.transacoes().isEmpty()) {
            throw ApiException.regraNegocio("STATEMENT_EMPTY", "O extrato não contém transações.");
        }
        if (!ignorarConferenciaConta && !mesmaConta(conta.getNumeroConta(), extrato.idConta())) {
            throw ApiException.regraNegocio("STATEMENT_ACCOUNT_MISMATCH", "O extrato é da conta " + extrato.idConta()
                    + ", diferente da conta cadastrada (" + conta.getNumeroConta() + "). Confira o arquivo.");
        }
        var usuario = acesso.usuario();
        ImportacaoExtrato importacao = importacaoRepository.save(new ImportacaoExtrato(condominio.getIdAdministradora(),
                idCondominio, idConta, nomeSeguro(nomeArquivo), hash, extrato.inicio(), extrato.fim(), extrato.transacoes().size(),
                usuario.idUsuario(), usuario.nome()));
        Set<String> existentes = linhaRepository.buscarFitIdsExistentes(idConta,
                extrato.transacoes().stream().map(OfxParser.Transacao::fitId).toList());
        int criadas = 0;
        for (OfxParser.Transacao t : extrato.transacoes()) {
            if (existentes.contains(t.fitId())) {
                continue;
            }
            linhaRepository.save(new LinhaExtrato(condominio.getIdAdministradora(), idCondominio, idConta, importacao.getId(),
                    t.fitId(), t.dataLancamento(), t.valor(), t.tipo(), t.historico()));
            criadas++;
        }
        linhaRepository.flush();
        int conciliadas = conciliarAutomaticamente(idCondominio, conta);
        importacao.registrarResultado(criadas, conciliadas);
        int pendentes = (int) linhaRepository.buscarPorConta(idConta, LinhaExtrato.Situacao.UNMATCHED).size();
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("account", conta.getNome());
        detalhes.put("file", importacao.getNomeArquivo());
        detalhes.put("newLines", criadas);
        detalhes.put("autoMatched", conciliadas);
        auditoriaService.registrar(AcaoAuditoria.STATEMENT_IMPORTED, "BankAccount", idConta, detalhes);
        return new ResultadoImportacao(importacao.getId(), importacao.getNomeArquivo(), extrato.inicio(), extrato.fim(),
                extrato.transacoes().size(), criadas, extrato.transacoes().size() - criadas, conciliadas, pendentes);
    }

    @Transactional(readOnly = true)
    public List<ResumoImportacao> importacoes(UUID idCondominio, UUID idConta) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        configuracao.carregarContaBancaria(idCondominio, idConta);
        return importacaoRepository.findTop20ByIdContaBancariaOrderByCriadoEmDesc(idConta).stream()
                .map(i -> new ResumoImportacao(i.getId(), i.getNomeArquivo(), i.getInicioPeriodo(), i.getFimPeriodo(),
                        i.getTotalLinhas(), i.getLinhasNovas(), i.getConciliadasAutomaticamente(), i.getCriadoEm(),
                        i.getNomeCriadoPor())).toList();
    }

    @Transactional(readOnly = true)
    public List<LinhaExtratoResponse> linhas(UUID idCondominio, UUID idConta, LinhaExtrato.Situacao situacao) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        configuracao.carregarContaBancaria(idCondominio, idConta);
        List<LinhaExtrato> lista = linhaRepository.buscarPorConta(idConta, situacao);
        List<Candidato> candidatos = lista.stream().anyMatch(l -> l.getSituacao() == LinhaExtrato.Situacao.UNMATCHED)
                ? candidatosEmAberto(idConta,
                lista.stream().map(LinhaExtrato::getDataLancamento).min(Comparator.naturalOrder()).orElseThrow(),
                lista.stream().map(LinhaExtrato::getDataLancamento).max(Comparator.naturalOrder()).orElseThrow(),
                TOLERANCIA_MANUAL_DIAS)
                : List.of();
        return lista.stream().map(l -> toResponse(l, l.getSituacao() == LinhaExtrato.Situacao.UNMATCHED
                ? ConciliacaoExtratoMatcher.sugestoes(toLinha(l), candidatos, TOLERANCIA_MANUAL_DIAS).stream().limit(5)
                .map(c -> new Sugestao(c.tipo(), c.id(), c.data(), c.valor(), c.descricao())).toList()
                : List.of())).toList();
    }

    @Transactional
    public LinhaExtratoResponse conciliar(UUID idCondominio, UUID idLinha, ConciliarRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        LinhaExtrato linha = carregar(idCondominio, idLinha);
        Candidato candidato = candidatosEmAberto(linha.getIdContaBancaria(), linha.getDataLancamento(), linha.getDataLancamento(),
                TOLERANCIA_MANUAL_DIAS)
                .stream().filter(c -> c.tipo().equals(request.tipo()) && c.id().equals(request.idMovimentacao())).findFirst()
                .orElseThrow(() -> ApiException.regraNegocio("MOVEMENT_NOT_ELIGIBLE",
                        "A movimentação não pertence a esta conta, já está conciliada ou está fora da janela de datas."));
        if (!ConciliacaoExtratoMatcher.compativel(toLinha(linha), candidato, TOLERANCIA_MANUAL_DIAS)) {
            throw ApiException.regraNegocio("AMOUNT_MISMATCH", "O valor da movimentação é diferente do valor do extrato.");
        }
        aplicar(idCondominio, linha, candidato);
        auditoriaService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "StatementLine", idLinha,
                Map.of("matched", candidato.chave()));
        return toResponse(linha, List.of());
    }

    @Transactional
    public LinhaExtratoResponse desfazerConciliacao(UUID idCondominio, UUID idLinha) {
        acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        LinhaExtrato linha = carregar(idCondominio, idLinha);
        bloqueioPeriodo.exigirAberto(idCondominio, linha.getDataLancamento());
        UUID por = acesso.usuario().idUsuario();
        Instant agora = Instant.now(clock);
        if (linha.getIdBaixa() != null) {
            baixaRepository.buscarTodasNoCondominio(List.of(linha.getIdBaixa()), idCondominio).forEach(p -> {
                bloqueioPeriodo.exigirAberto(idCondominio, p.getDataPagamento());
                p.conciliar(false, por, agora);
            });
        }
        if (linha.getIdTransferencia() != null) {
            transferenciaRepository.buscarTodasNoCondominio(List.of(linha.getIdTransferencia()), idCondominio).forEach(t -> {
                bloqueioPeriodo.exigirAberto(idCondominio, t.getData());
                t.conciliar(false, por, agora);
            });
        }
        linha.reabrir();
        auditoriaService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "StatementLine", idLinha, Map.of("matched", false));
        return toResponse(linha, List.of());
    }

    @Transactional
    public LinhaExtratoResponse ignorar(UUID idCondominio, UUID idLinha, IgnorarRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        LinhaExtrato linha = carregar(idCondominio, idLinha);
        bloqueioPeriodo.exigirAberto(idCondominio, linha.getDataLancamento());
        linha.ignorar(request.observacao().trim(), acesso.usuario().idUsuario(), Instant.now(clock));
        auditoriaService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "StatementLine", idLinha,
                Map.of("ignored", request.observacao().trim()));
        return toResponse(linha, List.of());
    }

    /**
     * Lança a receita/despesa que só existe no extrato (tarifa, rendimento, IOF) já baixada na data do extrato e a
     * concilia. Despesas em condomínio que exige aprovação passam pela aprovação — o usuário precisa dessa permissão.
     */
    @Transactional
    public LinhaExtratoResponse lancar(UUID idCondominio, UUID idLinha, LancarRequest request) {
        LinhaExtrato linha = carregar(idCondominio, idLinha);
        if (linha.getSituacao() != LinhaExtrato.Situacao.UNMATCHED) {
            throw ApiException.regraNegocio("STATEMENT_LINE_RESOLVED", "Esta linha do extrato já foi conciliada ou ignorada.");
        }
        Lancamento.Natureza natureza = linha.getValor().signum() > 0 ? Lancamento.Natureza.INCOME : Lancamento.Natureza.EXPENSE;
        BigDecimal valor = linha.getValor().abs();
        LancamentoDetalhe lancamento = lancamentoService.cadastrar(idCondominio, new LancamentoRequest(natureza,
                request.descricao(), request.idContaPlano(), request.idCentroCusto(), null, null, null, valor,
                linha.getDataLancamento(), linha.getDataLancamento(), null,
                "Lançado a partir do extrato bancário (" + linha.getFitId() + ")", null, null)).getFirst();
        if (lancamento.resumo().aprovacao() == Lancamento.Aprovacao.PENDING) {
            lancamento = lancamentoService.decidirAprovacao(idCondominio, lancamento.resumo().id(), true,
                    "Débito já efetivado no extrato bancário");
        }
        lancamento = lancamentoService.baixar(idCondominio, lancamento.resumo().id(), new BaixaRequest(linha.getIdContaBancaria(),
                linha.getDataLancamento(), valor, null, null, null, Baixa.FormaPagamento.DEBIT, "Extrato " + linha.getFitId()));
        UUID idBaixa = lancamento.baixas().getLast().id();
        Baixa baixa = baixaRepository.buscarTodasNoCondominio(List.of(idBaixa), idCondominio).getFirst();
        aplicar(idCondominio, linha, new Candidato("PAYMENT", idBaixa, baixa.getDataPagamento(), linha.getValor(),
                request.descricao()));
        return toResponse(linha, List.of());
    }

    // ------------------------------------------------------------------

    private int conciliarAutomaticamente(UUID idCondominio, ContaBancaria conta) {
        LocalDate fechado = bloqueioPeriodo.fechadoAte(idCondominio);
        List<LinhaExtrato> pendentes = linhaRepository.buscarPorConta(conta.getId(), LinhaExtrato.Situacao.UNMATCHED).stream()
                .filter(l -> fechado == null || l.getDataLancamento().isAfter(fechado)).toList();
        if (pendentes.isEmpty()) {
            return 0;
        }
        LocalDate de = pendentes.stream().map(LinhaExtrato::getDataLancamento).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate ate = pendentes.stream().map(LinhaExtrato::getDataLancamento).max(Comparator.naturalOrder()).orElseThrow();
        List<Candidato> candidatos = candidatosEmAberto(conta.getId(), de, ate, ConciliacaoExtratoMatcher.TOLERANCIA_PADRAO_DIAS)
                .stream().filter(c -> fechado == null || c.data().isAfter(fechado)).toList();
        Map<UUID, Candidato> pares = ConciliacaoExtratoMatcher.paresInequivocos(
                pendentes.stream().map(ImportacaoExtratoService::toLinha).toList(), candidatos,
                ConciliacaoExtratoMatcher.TOLERANCIA_PADRAO_DIAS);
        pendentes.stream().filter(l -> pares.containsKey(l.getId())).forEach(l -> aplicar(idCondominio, l, pares.get(l.getId())));
        return pares.size();
    }

    private void aplicar(UUID idCondominio, LinhaExtrato linha, Candidato c) {
        bloqueioPeriodo.exigirAberto(idCondominio, linha.getDataLancamento(), c.data());
        UUID por = acesso.usuario().idUsuario();
        Instant agora = Instant.now(clock);
        if ("PAYMENT".equals(c.tipo())) {
            baixaRepository.buscarTodasNoCondominio(List.of(c.id()), idCondominio).forEach(p -> p.conciliar(true, por, agora));
            linha.conciliarBaixa(c.id(), por, agora);
        } else {
            transferenciaRepository.buscarTodasNoCondominio(List.of(c.id()), idCondominio).forEach(t -> t.conciliar(true, por, agora));
            linha.conciliarTransferencia(c.id(), por, agora);
        }
    }

    /** Movimentações ainda não conciliadas da conta no intervalo (ampliado pela tolerância). */
    private List<Candidato> candidatosEmAberto(UUID idConta, LocalDate de, LocalDate ate, int toleranciaDias) {
        List<Candidato> resultado = new ArrayList<>();
        consultas.movimentacoes(idConta, BigDecimal.ZERO, de.minusDays(toleranciaDias), ate.plusDays(toleranciaDias)).stream()
                .filter(m -> !m.conciliada())
                .forEach(m -> resultado.add(new Candidato(m.tipo(), m.id(), m.data(), m.valor(), m.descricao())));
        return resultado;
    }

    private LinhaExtrato carregar(UUID idCondominio, UUID idLinha) {
        return linhaRepository.buscarNoCondominio(idLinha, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Linha do extrato"));
    }

    private static ConciliacaoExtratoMatcher.Linha toLinha(LinhaExtrato l) {
        return new ConciliacaoExtratoMatcher.Linha(l.getId(), l.getDataLancamento(), l.getValor());
    }

    private static LinhaExtratoResponse toResponse(LinhaExtrato l, List<Sugestao> sugestoes) {
        String tipo = l.getIdBaixa() != null ? "PAYMENT" : l.getIdTransferencia() != null ? "TRANSFER" : null;
        return new LinhaExtratoResponse(l.getId(), l.getFitId(), l.getDataLancamento(), l.getValor(), l.getTipoTransacao(),
                l.getHistorico(), l.getSituacao(), tipo, l.getIdBaixa() != null ? l.getIdBaixa() : l.getIdTransferencia(),
                l.getObservacao(), sugestoes);
    }

    /** Compara só os dígitos; aceita quando um contém o outro (com/sem dígito verificador, zeros à esquerda). */
    static boolean mesmaConta(String cadastrada, String doArquivo) {
        if (cadastrada == null || doArquivo == null) {
            return true;
        }
        String a = cadastrada.replaceAll("\\D", "").replaceFirst("^0+", "");
        String b = doArquivo.replaceAll("\\D", "").replaceFirst("^0+", "");
        return a.isEmpty() || b.isEmpty() || a.contains(b) || b.contains(a);
    }

    private static String nomeSeguro(String nome) {
        String n = nome == null || nome.isBlank() ? "extrato.ofx" : nome.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
