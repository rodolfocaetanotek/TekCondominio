package br.com.tekcondo.billing.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.billing.service.AllocationCalculator.Method;
import br.com.tekcondo.billing.service.AllocationCalculator.Share;
import br.com.tekcondo.comum.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationCalculatorTest {

    final UUID a = UUID.randomUUID();
    final UUID b = UUID.randomUUID();
    final UUID c = UUID.randomUUID();

    private static BigDecimal sum(Map<UUID, BigDecimal> m) {
        return m.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void idealFractionSumsExactlyAndGivesRoundingDifferenceToLargestFraction() {
        var units = List.of(new Share(a, new BigDecimal("33.333333")), new Share(b, new BigDecimal("33.333333")),
                new Share(c, new BigDecimal("33.333334")));
        var result = AllocationCalculator.allocate(Method.IDEAL_FRACTION, new BigDecimal("1000.00"), units, null);

        assertThat(sum(result)).isEqualByComparingTo("1000.00");
        assertThat(result.get(a)).isEqualByComparingTo("333.33");
        assertThat(result.get(c)).isEqualByComparingTo("333.34");
    }

    @Test
    void idealFractionRequiresHundredPercent() {
        var units = List.of(new Share(a, new BigDecimal("40")), new Share(b, new BigDecimal("50")));
        assertThatThrownBy(() -> AllocationCalculator.allocate(Method.IDEAL_FRACTION, BigDecimal.TEN, units, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("FRACTIONS_NOT_BALANCED"));
    }

    @Test
    void equalSplitDistributesRemainingCents() {
        var units = List.of(new Share(a, BigDecimal.ZERO), new Share(b, BigDecimal.ZERO), new Share(c, BigDecimal.ZERO));
        var result = AllocationCalculator.allocate(Method.EQUAL, new BigDecimal("100.00"), units, null);

        assertThat(result.get(a)).isEqualByComparingTo("33.34");
        assertThat(result.get(b)).isEqualByComparingTo("33.33");
        assertThat(result.get(c)).isEqualByComparingTo("33.33");
        assertThat(sum(result)).isEqualByComparingTo("100.00");
    }

    @Test
    void fixedChargesSameValuePerUnit() {
        var units = List.of(new Share(a, BigDecimal.ZERO), new Share(b, BigDecimal.ZERO));
        var result = AllocationCalculator.allocate(Method.FIXED, new BigDecimal("75.5"), units, null);
        assertThat(result.values()).allSatisfy(v -> assertThat(v).isEqualByComparingTo("75.50"));
    }

    @Test
    void consumptionUsesInformedValuesAndRejectsUnknownUnits() {
        var units = List.of(new Share(a, BigDecimal.ZERO), new Share(b, BigDecimal.ZERO));
        var result = AllocationCalculator.allocate(Method.CONSUMPTION, null, units,
                Map.of(a, new BigDecimal("42.10"), b, BigDecimal.ZERO));
        assertThat(result).containsOnlyKeys(a);

        assertThatThrownBy(() -> AllocationCalculator.allocate(Method.CONSUMPTION, null, units, Map.of(c, BigDecimal.ONE)))
                .isInstanceOf(ApiException.class);
    }
}
