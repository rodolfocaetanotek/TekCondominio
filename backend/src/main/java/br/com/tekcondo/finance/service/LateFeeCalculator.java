package br.com.tekcondo.finance.service;

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
public final class LateFeeCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_IN_MONTH = new BigDecimal("30");

    public record Quote(BigDecimal principal, long daysLate, BigDecimal fine, BigDecimal interest, BigDecimal total) {
    }

    private LateFeeCalculator() {
    }

    public static Quote quote(BigDecimal principal, LocalDate dueDate, LocalDate paymentDate, BigDecimal finePercent,
                              BigDecimal interestMonthlyPercent) {
        long days = Math.max(0, ChronoUnit.DAYS.between(dueDate, paymentDate));
        if (days == 0 || principal.signum() <= 0) {
            return new Quote(principal, 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    principal.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal fine = finePercent == null ? BigDecimal.ZERO
                : principal.multiply(finePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal interest = interestMonthlyPercent == null ? BigDecimal.ZERO
                : principal.multiply(interestMonthlyPercent)
                        .multiply(BigDecimal.valueOf(days))
                        .divide(HUNDRED.multiply(DAYS_IN_MONTH), 2, RoundingMode.HALF_UP);
        fine = fine.setScale(2, RoundingMode.HALF_UP);
        interest = interest.setScale(2, RoundingMode.HALF_UP);
        return new Quote(principal, days, fine, interest, principal.add(fine).add(interest).setScale(2, RoundingMode.HALF_UP));
    }
}
