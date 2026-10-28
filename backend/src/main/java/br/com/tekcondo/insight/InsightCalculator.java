package br.com.tekcondo.insight;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cálculos puros dos indicadores (sem acesso a banco), cobertos por testes unitários. */
public final class InsightCalculator {

    /** Valor em aberto com vencimento. {@code incoming} = a receber. */
    public record Due(LocalDate dueDate, BigDecimal amount, boolean incoming) {
    }

    public record Week(LocalDate start, LocalDate end, BigDecimal expectedIn, BigDecimal expectedOut, BigDecimal projectedBalance) {
    }

    /** Resultado da comparação de um mês com o histórico da conta. */
    public record Deviation(BigDecimal value, BigDecimal mean, BigDecimal stdDev, BigDecimal ratio) {
    }

    private InsightCalculator() {
    }

    /**
     * Projeção semanal a partir de {@code today}: apenas títulos com vencimento a partir de hoje entram nas semanas —
     * os vencidos não são presumidos como recebidos/pagos (o chamador os informa à parte).
     */
    public static List<Week> project(BigDecimal currentBalance, List<Due> dues, LocalDate today, int weeks) {
        List<Week> result = new ArrayList<>();
        BigDecimal balance = currentBalance;
        for (int w = 0; w < weeks; w++) {
            LocalDate start = today.plusWeeks(w);
            LocalDate end = start.plusDays(6);
            BigDecimal in = BigDecimal.ZERO;
            BigDecimal out = BigDecimal.ZERO;
            for (Due d : dues) {
                if (!d.dueDate().isBefore(start) && !d.dueDate().isAfter(end)) {
                    if (d.incoming()) in = in.add(d.amount());
                    else out = out.add(d.amount());
                }
            }
            balance = balance.add(in).subtract(out);
            result.add(new Week(start, end, in, out, balance));
        }
        return result;
    }

    /**
     * Despesa fora do padrão: exige ao menos {@code minHistory} meses com movimento no histórico e que o valor do mês
     * supere, ao mesmo tempo, média + 2 desvios-padrão, 130% da média e a média + {@code minAbsolute}.
     * Devolve nulo quando não há desvio (ou histórico insuficiente para afirmar).
     */
    public static Deviation deviation(BigDecimal value, List<BigDecimal> history, int minHistory, BigDecimal minAbsolute) {
        long withData = history.stream().filter(v -> v.signum() > 0).count();
        if (withData < minHistory || value.signum() <= 0) {
            return null;
        }
        MathContext mc = MathContext.DECIMAL64;
        BigDecimal n = BigDecimal.valueOf(history.size());
        BigDecimal mean = history.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(n, mc);
        BigDecimal variance = history.stream().map(v -> v.subtract(mean).pow(2, mc)).reduce(BigDecimal.ZERO, BigDecimal::add).divide(n, mc);
        BigDecimal std = variance.sqrt(mc);
        boolean outlier = value.compareTo(mean.add(std.multiply(BigDecimal.TWO))) > 0
                && value.compareTo(mean.multiply(new BigDecimal("1.3"))) > 0
                && value.subtract(mean).compareTo(minAbsolute) > 0;
        if (!outlier) {
            return null;
        }
        BigDecimal ratio = mean.signum() == 0 ? null : value.divide(mean, 2, RoundingMode.HALF_UP);
        return new Deviation(value, mean.setScale(2, RoundingMode.HALF_UP), std.setScale(2, RoundingMode.HALF_UP), ratio);
    }
}
