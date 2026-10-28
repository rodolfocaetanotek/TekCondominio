package br.com.tekcondo.insight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.tekcondo.insight.InsightCalculator.Due;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InsightCalculatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void projectionAccumulatesWeeklyAndIgnoresOverdueItems() {
        List<InsightCalculator.Week> weeks = InsightCalculator.project(d("1000.00"), List.of(
                new Due(TODAY.minusDays(5), d("999.00"), true),   // vencido: fora da projeção
                new Due(TODAY, d("300.00"), true),
                new Due(TODAY.plusDays(6), d("100.00"), false),
                new Due(TODAY.plusDays(7), d("50.00"), false)), TODAY, 3);

        assertThat(weeks).hasSize(3);
        assertThat(weeks.get(0).expectedIn()).isEqualByComparingTo("300.00");
        assertThat(weeks.get(0).expectedOut()).isEqualByComparingTo("100.00");
        assertThat(weeks.get(0).projectedBalance()).isEqualByComparingTo("1200.00");
        assertThat(weeks.get(1).projectedBalance()).isEqualByComparingTo("1150.00");
        assertThat(weeks.get(2).projectedBalance()).isEqualByComparingTo("1150.00");
    }

    @Test
    void flagsOnlyClearOutliersWithEnoughHistory() {
        List<BigDecimal> stable = List.of(d("1000"), d("1050"), d("980"), d("1020"), d("1000"), d("990"));
        InsightCalculator.Deviation spike = InsightCalculator.deviation(d("2500"), stable, 3, d("100"));
        assertThat(spike).isNotNull();
        assertThat(spike.mean()).isEqualByComparingTo("1006.67");
        assertThat(spike.ratio()).isEqualByComparingTo("2.48");

        assertThat(InsightCalculator.deviation(d("1100"), stable, 3, d("100"))).isNull(); // variação normal
        assertThat(InsightCalculator.deviation(d("2500"), List.of(d("0"), d("0"), d("1000"), d("0"), d("0"), d("0")), 3, d("100")))
                .isNull(); // histórico insuficiente: não afirma nada
        assertThat(InsightCalculator.deviation(d("60"), List.of(d("10"), d("10"), d("10"), d("10")), 3, d("100")))
                .isNull(); // diferença absoluta irrelevante
    }
}
