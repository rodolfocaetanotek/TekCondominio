package br.com.tekcondo.integracao.banco;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import br.com.tekcondo.comum.exception.ApiException;
import org.junit.jupiter.api.Test;

import static br.com.tekcondo.integracao.banco.Cnab240Fixture.header;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.segmentT;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.segmentU;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.trailer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetornoCnab240ParserTest {

    static final LocalDate DUE = LocalDate.of(2026, 10, 10);

    @Test
    void leSegmentosTEUComDecimaisImplicitos() {
        String file = String.join("\r\n", header("756", DUE.plusDays(3)),
                segmentT(1, "06", "0000123", "42", DUE, "500.00"),
                segmentU(2, "13.40", "0", "513.40", DUE.plusDays(2), DUE.plusDays(3)),
                segmentT(3, "02", "0000124", "43", DUE, "500.00"),
                segmentU(4, "0", "0", "0", DUE, DUE),
                trailer());
        RetornoCnab240Parser.Retorno r = RetornoCnab240Parser.parse(file.getBytes(StandardCharsets.ISO_8859_1));

        assertThat(r.codigoBanco()).isEqualTo("756");
        assertThat(r.dataGeracao()).isEqualTo(DUE.plusDays(3));
        assertThat(r.itens()).hasSize(2);
        RetornoCnab240Parser.Item paid = r.itens().getFirst();
        assertThat(paid.isLiquidacao()).isTrue();
        assertThat(paid.nossoNumero()).isEqualTo("0000123");
        assertThat(paid.seuNumero()).isEqualTo("42");
        assertThat(paid.valorTitulo()).isEqualByComparingTo("500.00");
        assertThat(paid.encargos()).isEqualByComparingTo("13.40");
        assertThat(paid.valorPago()).isEqualByComparingTo("513.40");
        assertThat(paid.tarifa()).isEqualByComparingTo("1.50");
        assertThat(paid.dataCredito()).isEqualTo(DUE.plusDays(3));
        assertThat(r.itens().get(1).isLiquidacao()).isFalse(); // 02 = entrada confirmada
    }

    @Test
    void rejeitaArquivosMalformados() {
        assertThatThrownBy(() -> RetornoCnab240Parser.parse("curto demais".getBytes()))
                .isInstanceOf(ApiException.class).hasMessageContaining("240");
        String orphanU = String.join("\n", header("756", DUE), segmentU(1, "0", "0", "1.00", DUE, DUE));
        assertThatThrownBy(() -> RetornoCnab240Parser.parse(orphanU.getBytes())).hasMessageContaining("segmento T");
    }
}
