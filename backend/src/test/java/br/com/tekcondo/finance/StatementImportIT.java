package br.com.tekcondo.finance;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatementImportIT extends IntegrationTest {

    static final String ACC_CONDO_FEE = "20000000-0000-0000-0000-000000000101";
    static final String ACC_BANK_FEES = "20000000-0000-0000-0000-000000000211";

    @Autowired
    ObjectMapper objectMapper;

    Administradora org;
    Condominio condo;
    String token;
    String base;
    String bank;
    LocalDate d1 = LocalDate.now().minusDays(5);

    @BeforeEach
    void setUp() throws Exception {
        org = data.administradora();
        condo = data.condominio(org, "Condomínio Extrato");
        token = TestData.bearer(data.login(data.adminAdministradora(org)));
        base = "/api/v1/condominios/" + condo.getId() + "/finance";
        bank = call("POST", base + "/bank-accounts", Map.of("name", "Sicoob", "kind", "CHECKING", "bankCode", "756",
                "agency", "4321", "accountNumber", "12345-6", "openingBalance", "1000.00",
                "openingDate", d1.minusMonths(1).toString()), 201).get("id").asText();
    }

    private JsonNode call(String method, String url, Object body, int expected) throws Exception {
        var builder = "POST".equals(method) ? post(url) : get(url);
        builder.header("Authorization", token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        }
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private JsonNode upload(String ofx, int expected) throws Exception {
        String json = mvc.perform(multipart(base + "/bank-accounts/" + bank + "/statement-imports")
                        .file(new MockMultipartFile("file", "extrato.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.ISO_8859_1)))
                        .header("Authorization", token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String receivedPayment(String amount, LocalDate paidOn) throws Exception {
        Map<String, Object> income = new HashMap<>();
        income.put("kind", "INCOME");
        income.put("description", "Taxa " + amount);
        income.put("chartAccountId", ACC_CONDO_FEE);
        income.put("amount", amount);
        income.put("dueDate", paidOn.toString());
        String id = call("POST", base + "/entries", income, 201).get(0).get("summary").get("id").asText();
        return call("POST", base + "/entries/" + id + "/payments", Map.of("bankAccountId", bank, "paidOn", paidOn.toString(),
                "principal", amount, "method", "PIX"), 200).get("payments").get(0).get("id").asText();
    }

    static String ofx(String account, String... transactions) {
        return "OFXHEADER:100\nDATA:OFXSGML\nCHARSET:1252\n\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>"
                + "<BANKACCTFROM><BANKID>756<ACCTID>" + account + "</BANKACCTFROM><BANKTRANLIST>\n"
                + String.join("\n", transactions) + "\n</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
    }

    static String trn(String fitId, LocalDate date, String amount, String memo) {
        return "<STMTTRN><TRNTYPE>OTHER<DTPOSTED>" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "<TRNAMT>" + amount
                + "<FITID>" + fitId + "<MEMO>" + memo + "</STMTTRN>";
    }

    private JsonNode line(String fitId) throws Exception {
        for (JsonNode l : call("GET", base + "/bank-accounts/" + bank + "/statement-lines", null, 200)) {
            if (l.get("fitId").asText().equals(fitId)) return l;
        }
        throw new AssertionError("linha não encontrada: " + fitId);
    }

    @Test
    void importAutoMatchesUnambiguousPairsAndSupportsManualResolution() throws Exception {
        String pay350 = receivedPayment("350.00", d1);
        receivedPayment("200.00", d1);
        String file = ofx("0012345-6",
                trn("F1", d1.plusDays(1), "350,00", "PIX APTO 101"),
                trn("F2", d1, "200,00", "PIX APTO 102"),
                trn("F3", d1.plusDays(2), "-12,90", "TARIFA PACOTE"),
                trn("F4", d1.plusDays(2), "999,00", "TED NAO IDENTIFICADA"));

        JsonNode result = upload(file, 201);
        assertThat(result.get("newLines").asInt()).isEqualTo(4);
        assertThat(result.get("autoMatched").asInt()).isEqualTo(2);
        assertThat(result.get("pending").asInt()).isEqualTo(2);
        assertThat(line("F1").get("status").asText()).isEqualTo("MATCHED");
        assertThat(line("F1").get("matchedId").asText()).isEqualTo(pay350);

        upload(file, 409); // mesmo arquivo
        JsonNode overlap = upload(ofx("12345-6", trn("F2", d1, "200,00", "PIX APTO 102"),
                trn("F5", d1.plusDays(3), "-5,00", "IOF")), 201);
        assertThat(overlap.get("duplicates").asInt()).isEqualTo(1);
        assertThat(overlap.get("newLines").asInt()).isEqualTo(1);

        // Tarifa só existe no extrato: lança, baixa e concilia de uma vez.
        JsonNode fee = call("POST", base + "/statement-lines/" + line("F3").get("id").asText() + "/register",
                Map.of("chartAccountId", ACC_BANK_FEES, "description", "Tarifa bancária"), 200);
        assertThat(fee.get("status").asText()).isEqualTo("MATCHED");
        JsonNode statement = call("GET", base + "/bank-accounts/" + bank + "/statement?from=" + d1.minusDays(1) + "&to=" + LocalDate.now(), null, 200);
        assertThat(statement.get("closingBalance").decimalValue()).isEqualByComparingTo("1537.10");

        call("POST", base + "/statement-lines/" + line("F4").get("id").asText() + "/ignore", Map.of("note", "Devolvida ao remetente"), 200);
        assertThat(line("F4").get("status").asText()).isEqualTo("IGNORED");

        // Desmarcar a conciliação no extrato do sistema libera a linha importada.
        call("POST", base + "/reconciliation", Map.of("reconciled", false, "items", List.of(Map.of("type", "PAYMENT", "id", pay350))), 200);
        JsonNode released = line("F1");
        assertThat(released.get("status").asText()).isEqualTo("UNMATCHED");
        assertThat(released.get("suggestions").get(0).get("id").asText()).isEqualTo(pay350);
        call("POST", base + "/statement-lines/" + released.get("id").asText() + "/match", Map.of("type", "PAYMENT", "movementId", pay350), 200);
        call("POST", base + "/statement-lines/" + released.get("id").asText() + "/match", Map.of("type", "PAYMENT", "movementId", pay350), 422);
    }

    @Test
    void rejectsStatementOfAnotherAccountAndOtherOrganizations() throws Exception {
        upload(ofx("99999-0", trn("X1", d1, "10,00", "PIX")), 422);
        String foreign = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/bank-accounts/" + bank + "/statement-lines").header("Authorization", foreign))
                .andExpect(status().isNotFound());
    }
}
