package br.com.tekcondo.indicador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.tekcondo.indicador.IndicadorCalculator.Vencimento;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndicadorCalculatorTest {

    static final LocalDate HOJE = LocalDate.of(2026, 10, 28);

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void projecaoAcumulaPorSemanaEIgnoraVencidos() {
        List<IndicadorCalculator.Semana> semanas = IndicadorCalculator.projetar(d("1000.00"), List.of(
                new Vencimento(HOJE.minusDays(5), d("999.00"), true),   // vencido: fora da projeção
                new Vencimento(HOJE, d("300.00"), true),
                new Vencimento(HOJE.plusDays(6), d("100.00"), false),
                new Vencimento(HOJE.plusDays(7), d("50.00"), false)), HOJE, 3);

        assertThat(semanas).hasSize(3);
        assertThat(semanas.get(0).entradasPrevistas()).isEqualByComparingTo("300.00");
        assertThat(semanas.get(0).saidasPrevistas()).isEqualByComparingTo("100.00");
        assertThat(semanas.get(0).saldoProjetado()).isEqualByComparingTo("1200.00");
        assertThat(semanas.get(1).saldoProjetado()).isEqualByComparingTo("1150.00");
        assertThat(semanas.get(2).saldoProjetado()).isEqualByComparingTo("1150.00");
    }

    @Test
    void apontaSomenteDesviosClarosComHistoricoSuficiente() {
        List<BigDecimal> estavel = List.of(d("1000"), d("1050"), d("980"), d("1020"), d("1000"), d("990"));
        IndicadorCalculator.Desvio pico = IndicadorCalculator.desvio(d("2500"), estavel, 3, d("100"));
        assertThat(pico).isNotNull();
        assertThat(pico.media()).isEqualByComparingTo("1006.67");
        assertThat(pico.razao()).isEqualByComparingTo("2.48");

        assertThat(IndicadorCalculator.desvio(d("1100"), estavel, 3, d("100"))).isNull(); // variação normal
        assertThat(IndicadorCalculator.desvio(d("2500"), List.of(d("0"), d("0"), d("1000"), d("0"), d("0"), d("0")), 3, d("100")))
                .isNull(); // histórico insuficiente: não afirma nada
        assertThat(IndicadorCalculator.desvio(d("60"), List.of(d("10"), d("10"), d("10"), d("10")), 3, d("100")))
                .isNull(); // diferença absoluta irrelevante
    }
}
