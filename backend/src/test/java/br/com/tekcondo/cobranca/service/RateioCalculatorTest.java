package br.com.tekcondo.cobranca.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.cobranca.service.RateioCalculator.Cota;
import br.com.tekcondo.cobranca.service.RateioCalculator.Criterio;
import br.com.tekcondo.comum.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateioCalculatorTest {

    final UUID a = UUID.randomUUID();
    final UUID b = UUID.randomUUID();
    final UUID c = UUID.randomUUID();

    private static BigDecimal soma(Map<UUID, BigDecimal> m) {
        return m.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void fracaoIdealSomaExatoEAtribuiDiferencaDeArredondamentoAMaiorFracao() {
        var unidades = List.of(new Cota(a, new BigDecimal("33.333333")), new Cota(b, new BigDecimal("33.333333")),
                new Cota(c, new BigDecimal("33.333334")));
        var resultado = RateioCalculator.ratear(Criterio.IDEAL_FRACTION, new BigDecimal("1000.00"), unidades, null);

        assertThat(soma(resultado)).isEqualByComparingTo("1000.00");
        assertThat(resultado.get(a)).isEqualByComparingTo("333.33");
        assertThat(resultado.get(c)).isEqualByComparingTo("333.34");
    }

    @Test
    void fracaoIdealExigeCemPorCento() {
        var unidades = List.of(new Cota(a, new BigDecimal("40")), new Cota(b, new BigDecimal("50")));
        assertThatThrownBy(() -> RateioCalculator.ratear(Criterio.IDEAL_FRACTION, BigDecimal.TEN, unidades, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("FRACTIONS_NOT_BALANCED"));
    }

    @Test
    void divisaoIgualitariaDistribuiCentavosRestantes() {
        var unidades = List.of(new Cota(a, BigDecimal.ZERO), new Cota(b, BigDecimal.ZERO), new Cota(c, BigDecimal.ZERO));
        var resultado = RateioCalculator.ratear(Criterio.EQUAL, new BigDecimal("100.00"), unidades, null);

        assertThat(resultado.get(a)).isEqualByComparingTo("33.34");
        assertThat(resultado.get(b)).isEqualByComparingTo("33.33");
        assertThat(resultado.get(c)).isEqualByComparingTo("33.33");
        assertThat(soma(resultado)).isEqualByComparingTo("100.00");
    }

    @Test
    void valorFixoCobraOMesmoValorPorUnidade() {
        var unidades = List.of(new Cota(a, BigDecimal.ZERO), new Cota(b, BigDecimal.ZERO));
        var resultado = RateioCalculator.ratear(Criterio.FIXED, new BigDecimal("75.5"), unidades, null);
        assertThat(resultado.values()).allSatisfy(v -> assertThat(v).isEqualByComparingTo("75.50"));
    }

    @Test
    void consumoUsaValoresInformadosERecusaUnidadesDesconhecidas() {
        var unidades = List.of(new Cota(a, BigDecimal.ZERO), new Cota(b, BigDecimal.ZERO));
        var resultado = RateioCalculator.ratear(Criterio.CONSUMPTION, null, unidades,
                Map.of(a, new BigDecimal("42.10"), b, BigDecimal.ZERO));
        assertThat(resultado).containsOnlyKeys(a);

        assertThatThrownBy(() -> RateioCalculator.ratear(Criterio.CONSUMPTION, null, unidades, Map.of(c, BigDecimal.ONE)))
                .isInstanceOf(ApiException.class);
    }
}
