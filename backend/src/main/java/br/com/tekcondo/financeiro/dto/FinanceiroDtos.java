package br.com.tekcondo.financeiro.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.Baixa;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.entity.Lancamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FinanceiroDtos {

    private FinanceiroDtos() {
    }

    // ---------- parâmetros
    public record ParametrosFinanceiros(
            @NotNull @DecimalMin(value = "0", message = "Multa não pode ser negativa.")
            @DecimalMax(value = "2", message = "A multa é limitada a 2% (CC, art. 1.336, §1º).") @Digits(integer = 1, fraction = 2) BigDecimal percentualMulta,
            @NotNull @DecimalMin(value = "0") @DecimalMax(value = "10", message = "Juros de até 10% ao mês.") @Digits(integer = 2, fraction = 2) BigDecimal percentualJurosMensal,
            @Min(value = 1, message = "Dia de vencimento entre 1 e 28.") @Max(value = 28, message = "Dia de vencimento entre 1 e 28.") int diaVencimento,
            boolean exigeAprovacaoContasPagar) {
    }

    // ---------- plano de contas
    public record ContaPlanoRequest(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String codigo,
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @NotNull(message = "Informe a natureza.") ContaPlano.Natureza natureza) {
    }

    public record ContaPlanoResponse(UUID id, String codigo, String nome, ContaPlano.Natureza natureza, boolean analitica,
                                     boolean sistema, boolean ativo) {

        public static ContaPlanoResponse de(ContaPlano a) {
            return new ContaPlanoResponse(a.getId(), a.getCodigo(), a.getNome(), a.getNatureza(), a.isAnalitica(), a.isSistema(),
                    a.isAtivo());
        }
    }

    // ---------- centros de custo e contas bancárias
    public record NomeRequest(@NotBlank(message = "Informe o nome.") @Size(max = 80) String nome) {
    }

    public record CentroCustoResponse(UUID id, String nome, boolean ativo) {
    }

    public record ContaBancariaRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 80) String nome,
            @NotNull(message = "Informe o tipo.") ContaBancaria.Tipo tipo,
            @Size(max = 5) String codigoBanco,
            @Size(max = 10) String agencia,
            @Size(max = 20) String numeroConta,
            @Digits(integer = 13, fraction = 2) BigDecimal saldoInicial,
            LocalDate dataAbertura) {
    }

    public record ContaBancariaResponse(UUID id, String nome, ContaBancaria.Tipo tipo, String codigoBanco, String agencia,
                                        String numeroConta, BigDecimal saldoInicial, LocalDate dataAbertura,
                                        boolean ativo, BigDecimal saldo) {
    }

    // ---------- lançamentos
    public record LancamentoRequest(
            @NotNull(message = "Informe se é receita ou despesa.") Lancamento.Natureza natureza,
            @NotBlank(message = "Informe a descrição.") @Size(max = 200) String descricao,
            @NotNull(message = "Informe a conta do plano de contas.") UUID idContaPlano,
            UUID idCentroCusto,
            UUID idFornecedor,
            UUID idUnidade,
            UUID idPessoa,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 13, fraction = 2, message = "Valor com no máximo 2 casas decimais.") BigDecimal valor,
            @NotNull(message = "Informe o vencimento.") LocalDate dataVencimento,
            LocalDate competencia,
            @Size(max = 40) String numeroDocumento,
            @Size(max = 500) String observacoes,
            @Min(value = 1, message = "Mínimo de 1 parcela.") @Max(value = 60, message = "Máximo de 60 parcelas/recorrências.") Integer repeticoes,
            Long versao) {
    }

    public record LancamentoResumo(UUID id, Lancamento.Natureza natureza, String descricao, String codigoConta,
                                   String nomeConta, String nomeFornecedor, String rotuloUnidade, String nomePessoa,
                                   BigDecimal valor, BigDecimal valorPago, BigDecimal valorAberto, LocalDate dataVencimento,
                                   LocalDate competencia, Lancamento.Situacao situacao, Lancamento.Aprovacao aprovacao,
                                   boolean vencido, String rotuloParcela, String numeroDocumento, Long numeroCobranca) {
    }

    public record BaixaResponse(UUID id, UUID idContaBancaria, String nomeContaBancaria, LocalDate dataPagamento,
                                BigDecimal principal, BigDecimal multa, BigDecimal juros, BigDecimal desconto,
                                BigDecimal total, Baixa.FormaPagamento formaPagamento, String observacoes, boolean conciliada,
                                boolean estornada, Instant estornadaEm, String motivoEstorno) {

        public static BaixaResponse de(Baixa p) {
            return new BaixaResponse(p.getId(), p.getContaBancaria().getId(), p.getContaBancaria().getNome(), p.getDataPagamento(),
                    p.getPrincipal(), p.getMulta(), p.getJuros(), p.getDesconto(), p.getTotal(), p.getFormaPagamento(),
                    p.getObservacoes(), p.getConciliadaEm() != null, p.isEstornada(), p.getEstornadaEm(), p.getMotivoEstorno());
        }
    }

    public record LancamentoDetalhe(LancamentoResumo resumo, UUID idContaPlano, UUID idCentroCusto, String nomeCentroCusto,
                                    UUID idFornecedor, UUID idUnidade, UUID idPessoa, String observacoes, Lancamento.Origem origem,
                                    String observacaoAprovacao, Instant aprovadoEm, String motivoCancelamento, Instant canceladoEm,
                                    BigDecimal percentualMulta, BigDecimal percentualJurosMensal, Long versao,
                                    List<BaixaResponse> baixas) {
    }

    public record DecisaoRequest(@Size(max = 255) String observacao) {
    }

    public record MotivoRequest(@NotBlank(message = "Informe a justificativa.") @Size(min = 5, max = 255,
            message = "A justificativa deve ter entre 5 e 255 caracteres.") String motivo) {
    }

    public record BaixaRequest(
            @NotNull(message = "Informe a conta de movimentação.") UUID idContaBancaria,
            @NotNull(message = "Informe a data do pagamento.") LocalDate dataPagamento,
            @NotNull(message = "Informe o valor principal.") @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero.")
            @Digits(integer = 13, fraction = 2) BigDecimal principal,
            @DecimalMin(value = "0") @Digits(integer = 13, fraction = 2) BigDecimal multa,
            @DecimalMin(value = "0") @Digits(integer = 13, fraction = 2) BigDecimal juros,
            @DecimalMin(value = "0") @Digits(integer = 13, fraction = 2) BigDecimal desconto,
            @NotNull(message = "Informe a forma de pagamento.") Baixa.FormaPagamento formaPagamento,
            @Size(max = 255) String observacoes) {
    }

    public record CalculoEncargos(BigDecimal principal, long diasAtraso, BigDecimal multa, BigDecimal juros, BigDecimal total) {
    }

    // ---------- transferências, extrato e conciliação
    public record TransferenciaRequest(
            @NotNull(message = "Informe a conta de origem.") UUID idContaOrigem,
            @NotNull(message = "Informe a conta de destino.") UUID idContaDestino,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero.")
            @Digits(integer = 13, fraction = 2) BigDecimal valor,
            @NotNull(message = "Informe a data.") LocalDate data,
            @Size(max = 200) String descricao) {
    }

    public record TransferenciaResponse(UUID id, UUID idContaOrigem, String nomeContaOrigem, UUID idContaDestino,
                                        String nomeContaDestino, BigDecimal valor, LocalDate data, String descricao,
                                        boolean conciliada, boolean estornada, String motivoEstorno) {
    }

    /** Movimentação do extrato da conta; {@code tipo}: PAYMENT, TRANSFER_IN ou TRANSFER_OUT (códigos estáveis). */
    public record Movimentacao(String tipo, UUID id, LocalDate data, String descricao, BigDecimal valor,
                               BigDecimal saldo, boolean conciliada) {
    }

    public record Extrato(UUID idContaBancaria, String nomeContaBancaria, LocalDate de, LocalDate ate,
                          BigDecimal saldoInicial, BigDecimal saldoFinal, BigDecimal saldoConciliado,
                          List<Movimentacao> movimentacoes) {
    }

    public record ConciliacaoRequest(@NotEmpty(message = "Selecione ao menos uma movimentação.") List<@Valid ItemConciliacao> itens,
                                     boolean conciliada) {
    }

    public record ItemConciliacao(@NotBlank String tipo, @NotNull UUID id) {
    }

    // ---------- orçamento
    public record CelulaOrcamento(@NotNull UUID idContaPlano, @Min(1) @Max(12) int mes,
                                  @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal valor) {
    }

    public record OrcamentoRequest(@NotNull List<@Valid CelulaOrcamento> celulas) {
    }

    public record ContaOrcamento(UUID idContaPlano, String codigo, String nome, ContaPlano.Natureza natureza,
                                 List<BigDecimal> previsto, List<BigDecimal> realizado, BigDecimal totalPrevisto,
                                 BigDecimal totalRealizado) {
    }

    public record Orcamento(int ano, List<ContaOrcamento> contas) {
    }
}
