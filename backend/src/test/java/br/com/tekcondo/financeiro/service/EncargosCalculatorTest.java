package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncargosCalculatorTest {

    private static final BigDecimal DOIS = new BigDecimal("2.00");
    private static final BigDecimal UM = new BigDecimal("1.00");

    @Test
    void semEncargosQuandoPagoAteOVencimento() {
        var c = EncargosCalculator.calcular(new BigDecimal("500.00"), LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10), DOIS, UM);
        assertThat(c.diasAtraso()).isZero();
        assertThat(c.multa()).isEqualByComparingTo("0");
        assertThat(c.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void multaAplicadaUmaVezEJurosProRataDie() {
        var c = EncargosCalculator.calcular(new BigDecimal("500.00"), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10), DOIS, UM);
        assertThat(c.diasAtraso()).isEqualTo(30);
        assertThat(c.multa()).isEqualByComparingTo("10.00");
        assertThat(c.juros()).isEqualByComparingTo("5.00");
        assertThat(c.total()).isEqualByComparingTo("515.00");

        var umDia = EncargosCalculator.calcular(new BigDecimal("350.00"), LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), DOIS, UM);
        assertThat(umDia.multa()).isEqualByComparingTo("7.00");
        assertThat(umDia.juros()).isEqualByComparingTo("0.12"); // 350 × 1% / 30 = 0,1166… → 0,12
    }

    @Test
    void arredondaCadaComponenteHalfUp() {
        var c = EncargosCalculator.calcular(new BigDecimal("333.33"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 16), DOIS, UM);
        assertThat(c.multa()).isEqualByComparingTo("6.67");      // 6,6666
        assertThat(c.juros()).isEqualByComparingTo("1.67");      // 333,33 × 0,01 × 15 / 30 = 1,666…
        assertThat(c.total()).isEqualByComparingTo("341.67");
    }

    @Test
    void titulosSemPercentuaisConfiguradosNaoTemEncargos() {
        var c = EncargosCalculator.calcular(new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null, null);
        assertThat(c.total()).isEqualByComparingTo("100.00");
        assertThat(c.diasAtraso()).isEqualTo(59);
    }
}
