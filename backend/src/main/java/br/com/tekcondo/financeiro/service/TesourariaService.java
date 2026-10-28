package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.Extrato;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ItemConciliacao;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.Movimentacao;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.TransferenciaRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.TransferenciaResponse;
import br.com.tekcondo.financeiro.entity.Baixa;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.LinhaExtrato;
import br.com.tekcondo.financeiro.entity.Transferencia;
import br.com.tekcondo.financeiro.repository.BaixaRepository;
import br.com.tekcondo.financeiro.repository.LinhaExtratoRepository;
import br.com.tekcondo.financeiro.repository.TransferenciaRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transferências entre contas, extrato com saldo e conciliação bancária. */
@Service
public class TesourariaService {

    private final TransferenciaRepository transferenciaRepository;
    private final BaixaRepository baixaRepository;
    private final LinhaExtratoRepository linhaExtratoRepository;
    private final ConfiguracaoFinanceiraService configuracao;
    private final ConsultasFinanceiras consultas;
    private final BloqueioPeriodo bloqueioPeriodo;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public TesourariaService(TransferenciaRepository transferenciaRepository, BaixaRepository baixaRepository,
                             LinhaExtratoRepository linhaExtratoRepository, ConfiguracaoFinanceiraService configuracao,
                             ConsultasFinanceiras consultas, BloqueioPeriodo bloqueioPeriodo, AcessoCondominio acesso,
                             AuditoriaService auditoriaService, Clock clock) {
        this.transferenciaRepository = transferenciaRepository;
        this.baixaRepository = baixaRepository;
        this.linhaExtratoRepository = linhaExtratoRepository;
        this.configuracao = configuracao;
        this.consultas = consultas;
        this.bloqueioPeriodo = bloqueioPeriodo;
        this.acesso = acesso;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TransferenciaResponse> transferencias(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        return transferenciaRepository.buscarDoCondominio(idCondominio).stream().map(TesourariaService::toResponse).toList();
    }

    @Transactional
    public TransferenciaResponse transferir(UUID idCondominio, TransferenciaRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        ContaBancaria origem = ativa(idCondominio, request.idContaOrigem());
        ContaBancaria destino = ativa(idCondominio, request.idContaDestino());
        if (request.data().isAfter(LocalDate.now(clock))) {
            throw ApiException.regraNegocio("FUTURE_TRANSFER", "A data da transferência não pode ser futura.");
        }
        bloqueioPeriodo.exigirAberto(idCondominio, request.data());
        BigDecimal disponivel = consultas.saldos(idCondominio, request.data()).getOrDefault(origem.getId(), origem.getSaldoInicial());
        if (origem.getTipo() == ContaBancaria.Tipo.CASH && disponivel.compareTo(request.valor()) < 0) {
            throw ApiException.regraNegocio("INSUFFICIENT_CASH", "Saldo de caixa insuficiente na data (R$ " + disponivel.toPlainString() + ").");
        }
        Transferencia transferencia = transferenciaRepository.save(new Transferencia(condominio.getIdAdministradora(), idCondominio,
                origem, destino, request.valor(), request.data(), emBranco(request.descricao()), acesso.usuario().idUsuario()));
        auditoriaService.registrar(AcaoAuditoria.TRANSFER_CREATED, "BankTransfer", transferencia.getId(), Map.of("from", origem.getNome(),
                "to", destino.getNome(), "amount", request.valor().toPlainString(), "date", request.data().toString()));
        return toResponse(transferencia);
    }

    @Transactional
    public TransferenciaResponse estornarTransferencia(UUID idCondominio, UUID id, String motivo) {
        acesso.exigir(idCondominio, Permissao.FINANCE_CANCEL);
        Transferencia transferencia = transferenciaRepository.findByIdAndIdCondominio(id, idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Transferência"));
        bloqueioPeriodo.exigirAberto(idCondominio, transferencia.getData());
        transferencia.estornar(motivo.trim(), acesso.usuario().idUsuario(), Instant.now(clock));
        auditoriaService.registrar(AcaoAuditoria.TRANSFER_REVERSED, "BankTransfer", id, Map.of("reason", motivo.trim()));
        return toResponse(transferencia);
    }

    @Transactional(readOnly = true)
    public Extrato extrato(UUID idCondominio, UUID idConta, LocalDate de, LocalDate ate) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        ContaBancaria conta = configuracao.carregarContaBancaria(idCondominio, idConta);
        LocalDate fim = ate == null ? LocalDate.now(clock) : ate;
        LocalDate inicio = de == null ? fim.withDayOfMonth(1) : de;
        if (inicio.isAfter(fim)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "A data inicial deve ser anterior à final.");
        }
        BigDecimal saldoInicial = consultas.saldoAntes(idConta, conta.getSaldoInicial(), inicio);
        List<Movimentacao> movimentacoes = consultas.movimentacoes(idConta, saldoInicial, inicio, fim);
        BigDecimal saldoFinal = movimentacoes.isEmpty() ? saldoInicial : movimentacoes.getLast().saldo();
        return new Extrato(idConta, conta.getNome(), inicio, fim, saldoInicial, saldoFinal,
                consultas.saldoConciliado(idConta, conta.getSaldoInicial(), fim), movimentacoes);
    }

    /** Marca/desmarca movimentações como conferidas com o extrato do banco. */
    @Transactional
    public int conciliar(UUID idCondominio, List<ItemConciliacao> itens, boolean conciliada) {
        acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        UUID por = acesso.usuario().idUsuario();
        Instant agora = Instant.now(clock);
        List<UUID> idsBaixa = itens.stream().filter(i -> "PAYMENT".equals(i.tipo())).map(ItemConciliacao::id).toList();
        List<UUID> idsTransferencia = itens.stream().filter(i -> i.tipo().startsWith("TRANSFER")).map(ItemConciliacao::id)
                .distinct().toList();
        List<Baixa> baixas = idsBaixa.isEmpty() ? List.of() : baixaRepository.buscarTodasNoCondominio(idsBaixa, idCondominio);
        List<Transferencia> transferencias = idsTransferencia.isEmpty() ? List.of()
                : transferenciaRepository.buscarTodasNoCondominio(idsTransferencia, idCondominio);
        if (baixas.size() != idsBaixa.size() || transferencias.size() != idsTransferencia.size()) {
            throw ApiException.naoEncontrado("Movimentação");
        }
        bloqueioPeriodo.exigirAberto(idCondominio, Stream.concat(baixas.stream().map(Baixa::getDataPagamento),
                transferencias.stream().map(Transferencia::getData)).toArray(LocalDate[]::new));
        baixas.forEach(p -> p.conciliar(conciliada, por, agora));
        transferencias.forEach(t -> t.conciliar(conciliada, por, agora));
        if (!conciliada) {
            // A linha do extrato importado casada com a movimentação volta a ficar pendente.
            baixas.forEach(p -> linhaExtratoRepository.findByIdBaixa(p.getId()).ifPresent(LinhaExtrato::reabrir));
            transferencias.forEach(t -> {
                linhaExtratoRepository.findByIdContaBancariaAndIdTransferencia(t.getContaOrigem().getId(), t.getId())
                        .ifPresent(LinhaExtrato::reabrir);
                linhaExtratoRepository.findByIdContaBancariaAndIdTransferencia(t.getContaDestino().getId(), t.getId())
                        .ifPresent(LinhaExtrato::reabrir);
            });
        }
        int total = baixas.size() + transferencias.size();
        auditoriaService.registrar(AcaoAuditoria.RECONCILIATION_CHANGED, "Condominium", idCondominio,
                Map.of("items", total, "reconciled", conciliada));
        return total;
    }

    private ContaBancaria ativa(UUID idCondominio, UUID id) {
        ContaBancaria conta = configuracao.carregarContaBancaria(idCondominio, id);
        if (!conta.isAtivo()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta " + conta.getNome() + " está inativa.");
        }
        return conta;
    }

    private static TransferenciaResponse toResponse(Transferencia t) {
        return new TransferenciaResponse(t.getId(), t.getContaOrigem().getId(), t.getContaOrigem().getNome(),
                t.getContaDestino().getId(), t.getContaDestino().getNome(), t.getValor(), t.getData(),
                t.getDescricao(), t.getConciliadaEm() != null, t.getEstornadaEm() != null, t.getMotivoEstorno());
    }

    private static String emBranco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
