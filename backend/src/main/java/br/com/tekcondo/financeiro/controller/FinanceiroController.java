package br.com.tekcondo.financeiro.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.financeiro.dto.FinanceiroDtos.CalculoEncargos;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.BaixaRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.CentroCustoResponse;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ConciliacaoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaBancariaRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaBancariaResponse;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaPlanoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaPlanoResponse;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.DecisaoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.Extrato;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoDetalhe;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoResumo;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.MotivoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.NomeRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.Orcamento;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.OrcamentoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ParametrosFinanceiros;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.TransferenciaRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.TransferenciaResponse;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.service.ConfiguracaoFinanceiraService;
import br.com.tekcondo.financeiro.service.LancamentoService;
import br.com.tekcondo.financeiro.service.OrcamentoService;
import br.com.tekcondo.financeiro.service.TesourariaService;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Financeiro")
public class FinanceiroController {

    private static final Map<String, String> ORDENS_LANCAMENTO = Map.of("dataVencimento", "dataVencimento", "valor", "valor",
            "descricao", "descricao", "criadoEm", "criadoEm", "competencia", "competencia");

    private final ConfiguracaoFinanceiraService configuracao;
    private final LancamentoService lancamentos;
    private final TesourariaService tesouraria;
    private final OrcamentoService orcamento;

    public FinanceiroController(ConfiguracaoFinanceiraService configuracao, LancamentoService lancamentos,
                                TesourariaService tesouraria, OrcamentoService orcamento) {
        this.configuracao = configuracao;
        this.lancamentos = lancamentos;
        this.tesouraria = tesouraria;
        this.orcamento = orcamento;
    }

    // ---------- plano de contas (da administradora)
    @GetMapping("/financeiro/plano-contas")
    @Operation(summary = "Plano de contas (padrão + contas próprias)")
    public List<ContaPlanoResponse> planoContas() {
        return configuracao.planoContas();
    }

    @PostMapping("/financeiro/plano-contas")
    public ResponseEntity<ContaPlanoResponse> cadastrarContaPlano(@Valid @RequestBody ContaPlanoRequest request) {
        return ResponseEntity.status(201).body(configuracao.cadastrarContaPlano(request));
    }

    @PutMapping("/financeiro/plano-contas/{id}")
    public ContaPlanoResponse atualizarContaPlano(@PathVariable UUID id, @Valid @RequestBody NomeRequest request,
                                                  @RequestParam(defaultValue = "true") boolean ativo) {
        return configuracao.atualizarContaPlano(id, request.nome(), ativo);
    }

    // ---------- parâmetros, centros de custo e contas
    @GetMapping("/condominios/{idCondominio}/financeiro/parametros")
    @Operation(summary = "Parâmetros financeiros (multa, juros, vencimento, aprovação de pagamentos)")
    public ParametrosFinanceiros parametros(@PathVariable UUID idCondominio) {
        return configuracao.parametros(idCondominio);
    }

    @PutMapping("/condominios/{idCondominio}/financeiro/parametros")
    public ParametrosFinanceiros atualizarParametros(@PathVariable UUID idCondominio,
                                                     @Valid @RequestBody ParametrosFinanceiros request) {
        return configuracao.atualizarParametros(idCondominio, request);
    }

    @GetMapping("/condominios/{idCondominio}/financeiro/centros-custo")
    public List<CentroCustoResponse> centrosCusto(@PathVariable UUID idCondominio) {
        return configuracao.centrosCusto(idCondominio);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/centros-custo")
    public ResponseEntity<CentroCustoResponse> cadastrarCentroCusto(@PathVariable UUID idCondominio,
                                                                    @Valid @RequestBody NomeRequest request) {
        return ResponseEntity.status(201).body(configuracao.salvarCentroCusto(idCondominio, null, request.nome(), null));
    }

    @PutMapping("/condominios/{idCondominio}/financeiro/centros-custo/{id}")
    public CentroCustoResponse atualizarCentroCusto(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                    @Valid @RequestBody NomeRequest request,
                                                    @RequestParam(required = false) Boolean ativo) {
        return configuracao.salvarCentroCusto(idCondominio, id, request.nome(), ativo);
    }

    @GetMapping("/condominios/{idCondominio}/financeiro/contas-bancarias")
    @Operation(summary = "Contas bancárias e caixa, com saldo atual calculado")
    public List<ContaBancariaResponse> contasBancarias(@PathVariable UUID idCondominio) {
        return configuracao.contasBancarias(idCondominio);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/contas-bancarias")
    public ResponseEntity<ContaBancariaResponse> cadastrarContaBancaria(@PathVariable UUID idCondominio,
                                                                        @Valid @RequestBody ContaBancariaRequest request) {
        return ResponseEntity.status(201).body(configuracao.cadastrarContaBancaria(idCondominio, request));
    }

    @PutMapping("/condominios/{idCondominio}/financeiro/contas-bancarias/{id}")
    public ContaBancariaResponse atualizarContaBancaria(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                        @Valid @RequestBody ContaBancariaRequest request,
                                                        @RequestParam(required = false) Boolean ativo) {
        return configuracao.atualizarContaBancaria(idCondominio, id, request, ativo);
    }

    @GetMapping("/condominios/{idCondominio}/financeiro/contas-bancarias/{id}/extrato")
    @Operation(summary = "Extrato da conta com saldo acumulado e situação de conciliação")
    public Extrato extrato(@PathVariable UUID idCondominio, @PathVariable UUID id,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return tesouraria.extrato(idCondominio, id, de, ate);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/conciliar")
    @Operation(summary = "Concilia (ou desfaz) movimentações com o extrato bancário")
    public Map<String, Integer> conciliar(@PathVariable UUID idCondominio, @Valid @RequestBody ConciliacaoRequest request) {
        return Map.of("atualizadas", tesouraria.conciliar(idCondominio, request.itens(), request.conciliada()));
    }

    // ---------- lançamentos
    @GetMapping("/condominios/{idCondominio}/financeiro/lancamentos")
    @Operation(summary = "Contas a pagar e a receber", description = "Filtros: natureza, situacao (lista), vencimentoDe, vencimentoAte, idContaPlano, idFornecedor, idUnidade, busca, vencido, aprovacao")
    public PaginaResponse<LancamentoResumo> lancamentos(@PathVariable UUID idCondominio,
                                                        @RequestParam(required = false) Lancamento.Natureza natureza,
                                                        @RequestParam(required = false) List<Lancamento.Situacao> situacao,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
                                                        @RequestParam(required = false) UUID idContaPlano,
                                                        @RequestParam(required = false) UUID idFornecedor,
                                                        @RequestParam(required = false) UUID idUnidade,
                                                        @RequestParam(required = false) String busca,
                                                        @RequestParam(required = false) Boolean vencido,
                                                        @RequestParam(required = false) Lancamento.Aprovacao aprovacao,
                                                        @RequestParam(defaultValue = "0") int pagina,
                                                        @RequestParam(defaultValue = "50") int tamanho,
                                                        @RequestParam(required = false) String ordem) {
        return lancamentos.listar(idCondominio, new LancamentoService.Filtro(natureza, situacao, vencimentoDe, vencimentoAte,
                        idContaPlano, idFornecedor, idUnidade, busca, vencido, aprovacao),
                PaginaRequests.of(pagina, tamanho, ordem, ORDENS_LANCAMENTO, Sort.by("dataVencimento").and(Sort.by("id"))));
    }

    @GetMapping("/condominios/{idCondominio}/financeiro/lancamentos/{id}")
    public LancamentoDetalhe lancamento(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return lancamentos.obter(idCondominio, id);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/lancamentos")
    @Operation(summary = "Lança receita ou despesa (repeticoes > 1 gera parcelas/recorrência mensal)")
    public ResponseEntity<List<LancamentoDetalhe>> cadastrarLancamento(@PathVariable UUID idCondominio,
                                                                       @Valid @RequestBody LancamentoRequest request) {
        return ResponseEntity.status(201).body(lancamentos.cadastrar(idCondominio, request));
    }

    @PutMapping("/condominios/{idCondominio}/financeiro/lancamentos/{id}")
    public LancamentoDetalhe atualizarLancamento(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                 @Valid @RequestBody LancamentoRequest request) {
        return lancamentos.atualizar(idCondominio, id, request);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/lancamentos/{id}/aprovar")
    public LancamentoDetalhe aprovar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                     @Valid @RequestBody(required = false) DecisaoRequest request) {
        return lancamentos.decidirAprovacao(idCondominio, id, true, request == null ? null : request.observacao());
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/lancamentos/{id}/reprovar")
    public LancamentoDetalhe reprovar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                      @Valid @RequestBody DecisaoRequest request) {
        return lancamentos.decidirAprovacao(idCondominio, id, false, request.observacao());
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/lancamentos/{id}/cancelar")
    @Operation(summary = "Cancela o lançamento (sem baixas ativas), com justificativa")
    public LancamentoDetalhe cancelar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                      @Valid @RequestBody MotivoRequest request) {
        return lancamentos.cancelar(idCondominio, id, request.motivo());
    }

    @GetMapping("/condominios/{idCondominio}/financeiro/lancamentos/{id}/encargos")
    @Operation(summary = "Calcula multa e juros para pagamento na data informada")
    public CalculoEncargos encargos(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return lancamentos.calcularEncargos(idCondominio, id, data);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/lancamentos/{id}/baixas")
    @Operation(summary = "Registra baixa total ou parcial")
    public LancamentoDetalhe baixar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                    @Valid @RequestBody BaixaRequest request) {
        return lancamentos.baixar(idCondominio, id, request);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/baixas/{idBaixa}/estornar")
    @Operation(summary = "Estorna uma baixa, com justificativa")
    public LancamentoDetalhe estornarBaixa(@PathVariable UUID idCondominio, @PathVariable UUID idBaixa,
                                           @Valid @RequestBody MotivoRequest request) {
        return lancamentos.estornarBaixa(idCondominio, idBaixa, request.motivo());
    }

    // ---------- transferências e orçamento
    @GetMapping("/condominios/{idCondominio}/financeiro/transferencias")
    public List<TransferenciaResponse> transferencias(@PathVariable UUID idCondominio) {
        return tesouraria.transferencias(idCondominio);
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/transferencias")
    public ResponseEntity<TransferenciaResponse> transferir(@PathVariable UUID idCondominio,
                                                            @Valid @RequestBody TransferenciaRequest request) {
        return ResponseEntity.status(201).body(tesouraria.transferir(idCondominio, request));
    }

    @PostMapping("/condominios/{idCondominio}/financeiro/transferencias/{id}/estornar")
    public TransferenciaResponse estornarTransferencia(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                       @Valid @RequestBody MotivoRequest request) {
        return tesouraria.estornarTransferencia(idCondominio, id, request.motivo());
    }

    @GetMapping("/condominios/{idCondominio}/financeiro/orcamento/{ano}")
    @Operation(summary = "Orçamento anual: previsto × realizado por conta e mês")
    public Orcamento orcamento(@PathVariable UUID idCondominio, @PathVariable int ano) {
        return orcamento.orcamento(idCondominio, ano);
    }

    @PutMapping("/condominios/{idCondominio}/financeiro/orcamento/{ano}")
    public Orcamento salvarOrcamento(@PathVariable UUID idCondominio, @PathVariable int ano,
                                     @Valid @RequestBody OrcamentoRequest request) {
        return orcamento.salvar(idCondominio, ano, request.celulas());
    }
}
