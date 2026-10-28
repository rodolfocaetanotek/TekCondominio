package br.com.tekcondo.estrutura.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.estrutura.entity.Unidade;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class EstruturaDtos {

    private EstruturaDtos() {
    }

    public record BlocoRequest(
            @NotBlank(message = "Informe o nome do bloco.") @Size(max = 80, message = "Máximo de 80 caracteres.") String nome,
            @Size(max = 255) String descricao) {
    }

    public record BlocoResponse(UUID id, String nome, String descricao, boolean ativo, long unidadesAtivas) {
    }

    public record UnidadeRequest(
            UUID idBloco,
            @NotBlank(message = "Informe a identificação da unidade.") @Size(max = 30, message = "Máximo de 30 caracteres.") String identificacao,
            @NotNull(message = "Informe o tipo.") Unidade.Tipo tipo,
            @Size(max = 10) String andar,
            @Positive(message = "A área deve ser positiva.") @Digits(integer = 8, fraction = 2, message = "Área com no máximo 2 casas decimais.") BigDecimal area,
            @DecimalMin(value = "0", message = "A fração ideal não pode ser negativa.")
            @DecimalMax(value = "100", message = "A fração ideal não pode passar de 100%.")
            @Digits(integer = 3, fraction = 6, message = "Fração ideal com no máximo 6 casas decimais.") BigDecimal fracaoIdeal,
            @Size(max = 500) String observacoes,
            Long versao) {
    }

    public record UnidadeResponse(UUID id, UUID idBloco, String nomeBloco, String identificacao, String rotulo,
                                  Unidade.Tipo tipo, String andar, BigDecimal area, BigDecimal fracaoIdeal,
                                  String observacoes, boolean ativo, Long versao, String nomeProprietario,
                                  String nomeResponsavelFinanceiro) {
    }

    /** Situação das frações ideais: o rateio por fração exige soma de 100% nas unidades ativas. */
    public record ResumoFracoes(BigDecimal total, long unidadesAtivas, boolean fechada, BigDecimal diferenca) {
    }

    public record ErroImportacao(int linha, String mensagem) {
    }

    public record ResultadoImportacao(boolean simulacao, int linhas, int blocosCriados, int unidadesCriadas,
                                      int pessoasCriadas, int pessoasReaproveitadas, List<ErroImportacao> erros) {
    }
}
