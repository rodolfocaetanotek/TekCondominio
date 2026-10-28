package br.com.tekcondo.integracao.banco;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import br.com.tekcondo.comum.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfxParserTest {

    static final String SGML = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            ENCODING:USASCII
            CHARSET:1252

            <OFX>
            <BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL
            <BANKACCTFROM><BANKID>0756<ACCTID>12345-6<ACCTTYPE>CHECKING</BANKACCTFROM>
            <BANKTRANLIST><DTSTART>20261001000000[-3:BRT]<DTEND>20261031000000[-3:BRT]
            <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261010120000[-3:BRT]<TRNAMT>350,00<FITID>A1<MEMO>PIX RECEBIDO APTO 101</STMTTRN>
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261012<TRNAMT>-1.234,56<FITID>A2<NAME>PAGTO BOLETO<MEMO>Elevadores Ltda</STMTTRN>
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261012<TRNAMT>-12.90<FITID>A2<MEMO>Tarifa pacote serviços</STMTTRN>
            <STMTTRN><TRNTYPE>OTHER<DTPOSTED>20261013<TRNAMT>0.00<FITID>A3<MEMO>Informativo</STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """;

    @Test
    void leSgmlComDecimaisBrasileirosEFitIdsDuplicados() {
        OfxParser.Extrato s = OfxParser.parse(SGML.getBytes(Charset.forName("windows-1252")));

        assertThat(s.idBanco()).isEqualTo("0756");
        assertThat(s.idConta()).isEqualTo("12345-6");
        assertThat(s.inicio()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(s.transacoes()).hasSize(3); // a transação de valor zero é descartada
        assertThat(s.transacoes().get(0).valor()).isEqualByComparingTo("350.00");
        assertThat(s.transacoes().get(0).dataLancamento()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(s.transacoes().get(1).valor()).isEqualByComparingTo("-1234.56");
        assertThat(s.transacoes().get(1).historico()).isEqualTo("PAGTO BOLETO — Elevadores Ltda");
        assertThat(s.transacoes().get(2).fitId()).isEqualTo("A2#2");
        assertThat(s.transacoes().get(2).historico()).isEqualTo("Tarifa pacote serviços");
    }

    @Test
    void leXmlOfx2() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><?OFX OFXHEADER="200" VERSION="211"?>
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20261005</DTPOSTED><TRNAMT>99.90</TRNAMT><FITID>X-1</FITID><MEMO>Rendimento &amp; juros</MEMO></STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
                """;
        OfxParser.Extrato s = OfxParser.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(s.transacoes()).singleElement().satisfies(t -> {
            assertThat(t.fitId()).isEqualTo("X-1");
            assertThat(t.valor()).isEqualByComparingTo("99.90");
            assertThat(t.historico()).isEqualTo("Rendimento & juros");
        });
    }

    @Test
    void rejeitaArquivosQueNaoSaoOfx() {
        assertThatThrownBy(() -> OfxParser.parse("data;valor\n01/10/2026;10,00".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class).hasMessageContaining("OFX");
    }
}
