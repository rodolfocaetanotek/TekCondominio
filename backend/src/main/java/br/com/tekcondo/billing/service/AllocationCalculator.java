package br.com.tekcondo.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;

/**
 * Rateio de um valor entre unidades (premissas P04/P05):
 * <ul>
 *   <li><b>IDEAL_FRACTION</b>: valor × fração/100; exige soma das frações = 100%. A diferença de arredondamento
 *       (centavos) é atribuída à unidade de maior fração (empate: primeira na ordem informada).</li>
 *   <li><b>EQUAL</b>: valor ÷ nº de unidades; os centavos restantes vão, um a um, às primeiras unidades.</li>
 *   <li><b>FIXED</b>: o mesmo valor para cada unidade (o total é valor × nº de unidades).</li>
 *   <li><b>CONSUMPTION</b>: valores individuais informados (ex.: leitura de água/gás).</li>
 * </ul>
 * O resultado sempre soma exatamente o total esperado.
 */
public final class AllocationCalculator {

    public enum Method {
        IDEAL_FRACTION, EQUAL, FIXED, CONSUMPTION
    }

    public record Share(UUID unitId, BigDecimal idealFraction) {
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private AllocationCalculator() {
    }

    public static Map<UUID, BigDecimal> allocate(Method method, BigDecimal amount, List<Share> units,
                                                 Map<UUID, BigDecimal> consumption) {
        if (units.isEmpty()) {
            throw ApiException.businessRule("NO_UNITS", "O condomínio não possui unidades ativas para o rateio.");
        }
        return switch (method) {
            case IDEAL_FRACTION -> byFraction(amount, units);
            case EQUAL -> equal(amount, units);
            case FIXED -> fixed(amount, units);
            case CONSUMPTION -> consumption(units, consumption);
        };
    }

    private static Map<UUID, BigDecimal> byFraction(BigDecimal total, List<Share> units) {
        BigDecimal fractions = units.stream().map(Share::idealFraction).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fractions.compareTo(HUNDRED) != 0) {
            throw ApiException.businessRule("FRACTIONS_NOT_BALANCED",
                    "A soma das frações ideais das unidades ativas é " + fractions.stripTrailingZeros().toPlainString()
                            + "%. Ajuste para 100% antes de ratear por fração ideal.");
        }
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Share s : units) {
            BigDecimal value = total.multiply(s.idealFraction()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            result.put(s.unitId(), value);
            sum = sum.add(value);
        }
        BigDecimal difference = total.subtract(sum);
        if (difference.signum() != 0) {
            Share largest = units.stream().max(Comparator.comparing(Share::idealFraction)).orElseThrow();
            result.computeIfPresent(largest.unitId(), (k, v) -> v.add(difference));
        }
        return result;
    }

    private static Map<UUID, BigDecimal> equal(BigDecimal total, List<Share> units) {
        int n = units.size();
        BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        int remainingCents = total.subtract(base.multiply(BigDecimal.valueOf(n))).divide(CENT, 0, RoundingMode.UNNECESSARY).intValueExact();
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            result.put(units.get(i).unitId(), i < remainingCents ? base.add(CENT) : base);
        }
        return result;
    }

    private static Map<UUID, BigDecimal> fixed(BigDecimal perUnit, List<Share> units) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        units.forEach(s -> result.put(s.unitId(), perUnit.setScale(2, RoundingMode.HALF_UP)));
        return result;
    }

    private static Map<UUID, BigDecimal> consumption(List<Share> units, Map<UUID, BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            throw ApiException.badRequest("CONSUMPTION_REQUIRED", "Informe o valor de consumo de cada unidade.");
        }
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (Share s : units) {
            BigDecimal value = values.get(s.unitId());
            if (value != null && value.signum() > 0) {
                result.put(s.unitId(), value.setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (values.keySet().stream().anyMatch(id -> units.stream().noneMatch(u -> u.unitId().equals(id)))) {
            throw ApiException.businessRule("UNKNOWN_UNIT", "Há valores de consumo para unidades inexistentes ou inativas.");
        }
        if (result.isEmpty()) {
            throw ApiException.businessRule("NO_CONSUMPTION", "Nenhuma unidade com consumo a cobrar.");
        }
        return result;
    }
}
