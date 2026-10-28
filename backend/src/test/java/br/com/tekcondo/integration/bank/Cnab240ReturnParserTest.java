package br.com.tekcondo.integration.bank;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import br.com.tekcondo.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import static br.com.tekcondo.integration.bank.Cnab240Fixture.header;
import static br.com.tekcondo.integration.bank.Cnab240Fixture.segmentT;
import static br.com.tekcondo.integration.bank.Cnab240Fixture.segmentU;
import static br.com.tekcondo.integration.bank.Cnab240Fixture.trailer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Cnab240ReturnParserTest {

    static final LocalDate DUE = LocalDate.of(2026, 10, 10);

    @Test
    void readsSegmentsTAndUWithImpliedDecimals() {
        String file = String.join("\r\n", header("756", DUE.plusDays(3)),
                segmentT(1, "06", "0000123", "42", DUE, "500.00"),
                segmentU(2, "13.40", "0", "513.40", DUE.plusDays(2), DUE.plusDays(3)),
                segmentT(3, "02", "0000124", "43", DUE, "500.00"),
                segmentU(4, "0", "0", "0", DUE, DUE),
                trailer());
        Cnab240ReturnParser.ReturnFile r = Cnab240ReturnParser.parse(file.getBytes(StandardCharsets.ISO_8859_1));

        assertThat(r.bankCode()).isEqualTo("756");
        assertThat(r.generatedOn()).isEqualTo(DUE.plusDays(3));
        assertThat(r.items()).hasSize(2);
        Cnab240ReturnParser.Item paid = r.items().getFirst();
        assertThat(paid.isSettlement()).isTrue();
        assertThat(paid.ourNumber()).isEqualTo("0000123");
        assertThat(paid.yourNumber()).isEqualTo("42");
        assertThat(paid.faceValue()).isEqualByComparingTo("500.00");
        assertThat(paid.feesAndCharges()).isEqualByComparingTo("13.40");
        assertThat(paid.paidAmount()).isEqualByComparingTo("513.40");
        assertThat(paid.bankFee()).isEqualByComparingTo("1.50");
        assertThat(paid.creditDate()).isEqualTo(DUE.plusDays(3));
        assertThat(r.items().get(1).isSettlement()).isFalse(); // 02 = entrada confirmada
    }

    @Test
    void rejectsMalformedFiles() {
        assertThatThrownBy(() -> Cnab240ReturnParser.parse("curto demais".getBytes()))
                .isInstanceOf(ApiException.class).hasMessageContaining("240");
        String orphanU = String.join("\n", header("756", DUE), segmentU(1, "0", "0", "1.00", DUE, DUE));
        assertThatThrownBy(() -> Cnab240ReturnParser.parse(orphanU.getBytes())).hasMessageContaining("segmento T");
    }
}
