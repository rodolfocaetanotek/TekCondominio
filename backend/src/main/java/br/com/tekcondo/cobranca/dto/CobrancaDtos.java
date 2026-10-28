package br.com.tekcondo.cobranca.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.cobranca.entity.Acordo;
import br.com.tekcondo.cobranca.entity.NotaCobranca;
import br.com.tekcondo.cobranca.entity.Rateio;
import br.com.tekcondo.cobranca.service.RateioCalculator;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoResumo;
import br.com.tekcondo.financeiro.entity.Lancamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CobrancaDtos {

    private CobrancaDtos() {
    }

    // ---------- rateio / geração de cobranças
    public record RateioRequest(
            @NotBlank(message = "Informe a competência.") @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Competência no formato AAAA-MM.") String competencia,
            @NotBlank(message = "Informe a descrição.") @Size(max = 200) String descricao,
            @NotNull(message = "Informe a conta de receita.") UUID idContaPlano,
            @NotNull(message = "Informe o critério de rateio.") RateioCalculator.Criterio criterio,
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.") @Digits(integer = 13, fraction = 2) BigDecimal valor,
            LocalDate dataVencimento,
            Map<UUID, BigDecimal> consumo) {
    }

    public record LinhaRateio(UUID idUnidade, String rotuloUnidade, BigDecimal fracaoIdeal, String nomeResponsavel, BigDecimal valor) {
    }

    public record SimulacaoRateio(LocalDate competencia, LocalDate dataVencimento, BigDecimal total, int unidades,
                                  int unidadesSemResponsavel, List<LinhaRateio> linhas) {
    }

    public record RateioResponse(UUID id, LocalDate competencia, String descricao, String codigoConta, String nomeConta,
                                 RateioCalculator.Criterio criterio, BigDecimal valorBase, LocalDate dataVencimento,
                                 int quantidadeUnidades, BigDecimal valorGerado, Rateio.Situacao situacao, long quantidadePagas,
                                 BigDecimal valorRecebido, String motivoCancelamento, Instant criadoEm) {
    }

    // ---------- inadimplência
    public record LinhaInadimplencia(UUID idUnidade, String rotuloUnidade, String nomeResponsavel, String emailResponsavel,
                                     String telefoneResponsavel, int quantidadeVencidas, BigDecimal principal, BigDecimal encargos,
                                     BigDecimal total, LocalDate vencimentoMaisAntigo, long maximoDiasAtraso, LocalDate dataUltimoContato) {
    }

    public record Inadimplencia(LocalDate dataBase, BigDecimal totalPrincipal, BigDecimal totalEncargos, BigDecimal total,
                                BigDecimal emitidoUltimos12Meses, BigDecimal indiceInadimplencia, int unidadesEmDebito,
                                long unidadesAtivas, List<LinhaInadimplencia> linhas) {
    }

    public record LinhaDebito(UUID idLancamento, String descricao, LocalDate competencia, LocalDate dataVencimento, BigDecimal valor,
                              BigDecimal valorPago, BigDecimal valorAberto, Lancamento.Situacao situacao, long diasAtraso,
                              BigDecimal multa, BigDecimal juros, BigDecimal totalAtualizado) {
    }

    public record NotaCobrancaRequest(@NotNull(message = "Informe o tipo de contato.") NotaCobranca.TipoContato tipoContato,
                                      @NotNull(message = "Informe a data do contato.") LocalDate dataContato,
                                      @NotBlank(message = "Descreva o contato.") @Size(max = 1000) String observacao,
                                      UUID idLancamento) {
    }

    public record NotaCobrancaResponse(UUID id, NotaCobranca.TipoContato tipoContato, LocalDate dataContato, String observacao,
                                       UUID idLancamento, String nomeCriadoPor, Instant criadoEm) {

        public static NotaCobrancaResponse de(NotaCobranca n) {
            return new NotaCobrancaResponse(n.getId(), n.getTipoContato(), n.getDataContato(), n.getObservacao(), n.getIdLancamento(),
                    n.getNomeCriadoPor(), n.getCriadoEm());
        }
    }

    public record DebitosUnidade(UUID idUnidade, String rotuloUnidade, String nomeResponsavel, LocalDate dataBase, BigDecimal principalAberto,
                                 BigDecimal encargosAbertos, BigDecimal totalAberto, List<LinhaDebito> linhas, List<NotaCobrancaResponse> notas,
                                 List<AcordoResponse> acordos) {
    }

    // ---------- acordos
    public record AcordoRequest(
            @NotNull(message = "Informe a unidade.") UUID idUnidade,
            @NotEmpty(message = "Selecione os débitos a renegociar.") List<UUID> idsLancamentos,
            @Min(value = 1, message = "Mínimo de 1 parcela.") @Max(value = 120, message = "Máximo de 120 parcelas.") int totalParcelas,
            @NotNull(message = "Informe o vencimento da 1ª parcela.") LocalDate dataPrimeiroVencimento,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal desconto,
            boolean incluirEncargos,
            @Size(max = 500) String observacoes) {
    }

    public record AcordoResponse(UUID id, UUID idUnidade, String rotuloUnidade, BigDecimal valorOriginal, BigDecimal valorEncargos,
                                 BigDecimal desconto, BigDecimal valorTotal, int totalParcelas, LocalDate dataPrimeiroVencimento,
                                 Acordo.Situacao situacao, long parcelasPagas, String observacoes, String motivoCancelamento,
                                 Instant criadoEm, List<LancamentoResumo> parcelas) {
    }
}
