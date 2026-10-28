package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LateFeeCalculatorTest {

    private static final BigDecimal TWO = new BigDecimal("2.00");
    private static final BigDecimal ONE = new BigDecimal("1.00");

    @Test
    void noChargesWhenPaidOnOrBeforeDueDate() {
        var q = LateFeeCalculator.quote(new BigDecimal("500.00"), LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10), TWO, ONE);
        assertThat(q.daysLate()).isZero();
        assertThat(q.fine()).isEqualByComparingTo("0");
        assertThat(q.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void fineIsAppliedOnceAndInterestProRataDie() {
        var q = LateFeeCalculator.quote(new BigDecimal("500.00"), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10), TWO, ONE);
        assertThat(q.daysLate()).isEqualTo(30);
        assertThat(q.fine()).isEqualByComparingTo("10.00");
        assertThat(q.interest()).isEqualByComparingTo("5.00");
        assertThat(q.total()).isEqualByComparingTo("515.00");

        var oneDay = LateFeeCalculator.quote(new BigDecimal("350.00"), LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), TWO, ONE);
        assertThat(oneDay.fine()).isEqualByComparingTo("7.00");
        assertThat(oneDay.interest()).isEqualByComparingTo("0.12"); // 350 × 1% / 30 = 0,1166… → 0,12
    }

    @Test
    void roundsEachComponentHalfUp() {
        var q = LateFeeCalculator.quote(new BigDecimal("333.33"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 16), TWO, ONE);
        assertThat(q.fine()).isEqualByComparingTo("6.67");      // 6,6666
        assertThat(q.interest()).isEqualByComparingTo("1.67");  // 333,33 × 0,01 × 15 / 30 = 1,666…
        assertThat(q.total()).isEqualByComparingTo("341.67");
    }

    @Test
    void entriesWithoutConfiguredRatesHaveNoCharges() {
        var q = LateFeeCalculator.quote(new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null, null);
        assertThat(q.total()).isEqualByComparingTo("100.00");
        assertThat(q.daysLate()).isEqualTo(59);
    }
}
