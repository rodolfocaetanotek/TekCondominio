package br.com.tekcondo.integration.bank;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import br.com.tekcondo.shared.exception.ApiException;
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
    void parsesSgmlWithBrazilianDecimalsAndDuplicatedFitIds() {
        OfxParser.Statement s = OfxParser.parse(SGML.getBytes(Charset.forName("windows-1252")));

        assertThat(s.bankId()).isEqualTo("0756");
        assertThat(s.accountId()).isEqualTo("12345-6");
        assertThat(s.start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(s.transactions()).hasSize(3); // a transação de valor zero é descartada
        assertThat(s.transactions().get(0).amount()).isEqualByComparingTo("350.00");
        assertThat(s.transactions().get(0).postedOn()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(s.transactions().get(1).amount()).isEqualByComparingTo("-1234.56");
        assertThat(s.transactions().get(1).memo()).isEqualTo("PAGTO BOLETO — Elevadores Ltda");
        assertThat(s.transactions().get(2).fitId()).isEqualTo("A2#2");
        assertThat(s.transactions().get(2).memo()).isEqualTo("Tarifa pacote serviços");
    }

    @Test
    void parsesXmlOfx2() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><?OFX OFXHEADER="200" VERSION="211"?>
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20261005</DTPOSTED><TRNAMT>99.90</TRNAMT><FITID>X-1</FITID><MEMO>Rendimento &amp; juros</MEMO></STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
                """;
        OfxParser.Statement s = OfxParser.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(s.transactions()).singleElement().satisfies(t -> {
            assertThat(t.fitId()).isEqualTo("X-1");
            assertThat(t.amount()).isEqualByComparingTo("99.90");
            assertThat(t.memo()).isEqualTo("Rendimento & juros");
        });
    }

    @Test
    void rejectsFilesThatAreNotOfx() {
        assertThatThrownBy(() -> OfxParser.parse("data;valor\n01/10/2026;10,00".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class).hasMessageContaining("OFX");
    }
}
