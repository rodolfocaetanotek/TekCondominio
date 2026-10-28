package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Encargos por atraso (premissas P01, P02 e P05 de ARQUITETURA.md):
 * <ul>
 *   <li>Multa: percentual único sobre o principal em aberto, se pago após o vencimento.</li>
 *   <li>Juros: simples, pró-rata die — principal × (taxa mensal / 30) × dias de atraso.</li>
 *   <li>Arredondamento: 2 casas, HALF_UP, ao final de cada componente.</li>
 *   <li>Correção monetária: não aplicada automaticamente (sem índice configurado — premissa P03).</li>
 * </ul>
 * Os percentuais vêm do título (fotografados na emissão), não do condomínio atual.
 */
public final class EncargosCalculator {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal DIAS_NO_MES = new BigDecimal("30");

    public record Calculo(BigDecimal principal, long diasAtraso, BigDecimal multa, BigDecimal juros, BigDecimal total) {
    }

    private EncargosCalculator() {
    }

    public static Calculo calcular(BigDecimal principal, LocalDate dataVencimento, LocalDate dataPagamento,
                                   BigDecimal percentualMulta, BigDecimal percentualJurosMensal) {
        long dias = Math.max(0, ChronoUnit.DAYS.between(dataVencimento, dataPagamento));
        if (dias == 0 || principal.signum() <= 0) {
            return new Calculo(principal, 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    principal.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal multa = percentualMulta == null ? BigDecimal.ZERO
                : principal.multiply(percentualMulta).divide(CEM, 2, RoundingMode.HALF_UP);
        BigDecimal juros = percentualJurosMensal == null ? BigDecimal.ZERO
                : principal.multiply(percentualJurosMensal)
                        .multiply(BigDecimal.valueOf(dias))
                        .divide(CEM.multiply(DIAS_NO_MES), 2, RoundingMode.HALF_UP);
        multa = multa.setScale(2, RoundingMode.HALF_UP);
        juros = juros.setScale(2, RoundingMode.HALF_UP);
        return new Calculo(principal, dias, multa, juros, principal.add(multa).add(juros).setScale(2, RoundingMode.HALF_UP));
    }
}
