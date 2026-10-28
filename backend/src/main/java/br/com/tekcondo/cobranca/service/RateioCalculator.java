package br.com.tekcondo.cobranca.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;

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
public final class RateioCalculator {

    public enum Criterio {
        IDEAL_FRACTION, EQUAL, FIXED, CONSUMPTION
    }

    public record Cota(UUID idUnidade, BigDecimal fracaoIdeal) {
    }

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal CENTAVO = new BigDecimal("0.01");

    private RateioCalculator() {
    }

    public static Map<UUID, BigDecimal> ratear(Criterio criterio, BigDecimal valor, List<Cota> unidades,
                                               Map<UUID, BigDecimal> consumo) {
        if (unidades.isEmpty()) {
            throw ApiException.regraNegocio("NO_UNITS", "O condomínio não possui unidades ativas para o rateio.");
        }
        return switch (criterio) {
            case IDEAL_FRACTION -> porFracao(valor, unidades);
            case EQUAL -> igualitario(valor, unidades);
            case FIXED -> fixo(valor, unidades);
            case CONSUMPTION -> porConsumo(unidades, consumo);
        };
    }

    private static Map<UUID, BigDecimal> porFracao(BigDecimal total, List<Cota> unidades) {
        BigDecimal fracoes = unidades.stream().map(Cota::fracaoIdeal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fracoes.compareTo(CEM) != 0) {
            throw ApiException.regraNegocio("FRACTIONS_NOT_BALANCED",
                    "A soma das frações ideais das unidades ativas é " + fracoes.stripTrailingZeros().toPlainString()
                            + "%. Ajuste para 100% antes de ratear por fração ideal.");
        }
        Map<UUID, BigDecimal> resultado = new LinkedHashMap<>();
        BigDecimal soma = BigDecimal.ZERO;
        for (Cota c : unidades) {
            BigDecimal valor = total.multiply(c.fracaoIdeal()).divide(CEM, 2, RoundingMode.HALF_UP);
            resultado.put(c.idUnidade(), valor);
            soma = soma.add(valor);
        }
        BigDecimal diferenca = total.subtract(soma);
        if (diferenca.signum() != 0) {
            Cota maior = unidades.stream().max(Comparator.comparing(Cota::fracaoIdeal)).orElseThrow();
            resultado.computeIfPresent(maior.idUnidade(), (k, v) -> v.add(diferenca));
        }
        return resultado;
    }

    private static Map<UUID, BigDecimal> igualitario(BigDecimal total, List<Cota> unidades) {
        int n = unidades.size();
        BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        int centavosRestantes = total.subtract(base.multiply(BigDecimal.valueOf(n))).divide(CENTAVO, 0, RoundingMode.UNNECESSARY).intValueExact();
        Map<UUID, BigDecimal> resultado = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            resultado.put(unidades.get(i).idUnidade(), i < centavosRestantes ? base.add(CENTAVO) : base);
        }
        return resultado;
    }

    private static Map<UUID, BigDecimal> fixo(BigDecimal porUnidade, List<Cota> unidades) {
        Map<UUID, BigDecimal> resultado = new LinkedHashMap<>();
        unidades.forEach(c -> resultado.put(c.idUnidade(), porUnidade.setScale(2, RoundingMode.HALF_UP)));
        return resultado;
    }

    private static Map<UUID, BigDecimal> porConsumo(List<Cota> unidades, Map<UUID, BigDecimal> valores) {
        if (valores == null || valores.isEmpty()) {
            throw ApiException.requisicaoInvalida("CONSUMPTION_REQUIRED", "Informe o valor de consumo de cada unidade.");
        }
        Map<UUID, BigDecimal> resultado = new LinkedHashMap<>();
        for (Cota c : unidades) {
            BigDecimal valor = valores.get(c.idUnidade());
            if (valor != null && valor.signum() > 0) {
                resultado.put(c.idUnidade(), valor.setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (valores.keySet().stream().anyMatch(id -> unidades.stream().noneMatch(u -> u.idUnidade().equals(id)))) {
            throw ApiException.regraNegocio("UNKNOWN_UNIT", "Há valores de consumo para unidades inexistentes ou inativas.");
        }
        if (resultado.isEmpty()) {
            throw ApiException.regraNegocio("NO_CONSUMPTION", "Nenhuma unidade com consumo a cobrar.");
        }
        return resultado;
    }
}
