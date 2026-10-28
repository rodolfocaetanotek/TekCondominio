package br.com.tekcondo.billing;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
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

import static br.com.tekcondo.integracao.banco.Cnab240Fixture.header;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.segmentT;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.segmentU;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.trailer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankReturnIT extends IntegrationTest {

    static final String ACC_CONDO_FEE = "20000000-0000-0000-0000-000000000101";

    @Autowired
    ObjectMapper objectMapper;

    Administradora org;
    Condominio condo;
    String token;
    String base;
    String bank;
    String unit;
    LocalDate today = LocalDate.now();
    LocalDate due = today.minusDays(10);

    @BeforeEach
    void setUp() throws Exception {
        org = data.administradora();
        condo = data.condominio(org, "Condomínio Retorno");
        token = TestData.bearer(data.login(data.adminAdministradora(org)));
        base = "/api/v1/condominios/" + condo.getId();
        bank = call("POST", base + "/financeiro/contas-bancarias", Map.of("nome", "Sicoob Cobrança", "tipo", "CHECKING", "codigoBanco", "756",
                "saldoInicial", "0", "dataAbertura", today.minusMonths(2).toString()), 201).get("id").asText();
        unit = call("POST", base + "/unidades", Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "10"), 201).get("id").asText();
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

    private JsonNode upload(String account, String content, int expected) throws Exception {
        String json = mvc.perform(multipart(base + "/billing/bank-returns")
                        .file(new MockMultipartFile("file", "COB.RET", "text/plain", content.getBytes(StandardCharsets.ISO_8859_1)))
                        .param("bankAccountId", account).header("Authorization", token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode charge(String amount) throws Exception {
        Map<String, Object> income = new HashMap<>();
        income.put("natureza", "INCOME");
        income.put("descricao", "Taxa condominial");
        income.put("idContaPlano", ACC_CONDO_FEE);
        income.put("valor", amount);
        income.put("dataVencimento", due.toString());
        income.put("idUnidade", unit);
        return call("POST", base + "/financeiro/lancamentos", income, 201).get(0).get("resumo");
    }

    @Test
    void settlementsAreBookedOnceWithFineAndInterestSplit() throws Exception {
        JsonNode first = charge("500.00");
        JsonNode second = charge("500.00");
        long number = first.get("numeroCobranca").asLong();
        assertThat(second.get("numeroCobranca").asLong()).isNotEqualTo(number);

        LocalDate paid = today.minusDays(2);
        String file = String.join("\r\n", header("756", today),
                segmentT(1, "06", "", String.valueOf(number), due, "500.00"),
                segmentU(2, "13.40", "0", "513.40", paid, paid.plusDays(1)),
                segmentT(3, "06", "", "99999999", due, "500.00"),
                segmentU(4, "0", "0", "500.00", paid, paid),
                segmentT(5, "02", "", String.valueOf(second.get("numeroCobranca").asLong()), due, "500.00"),
                segmentU(6, "0", "0", "0", paid, paid),
                trailer());

        JsonNode result = upload(bank, file, 201);
        assertThat(result.get("settled").asInt()).isEqualTo(1);
        assertThat(result.get("pending").asInt()).isEqualTo(1);
        upload(bank, file, 409);

        JsonNode entry = call("GET", base + "/financeiro/lancamentos/" + first.get("id").asText(), null, 200);
        assertThat(entry.get("resumo").get("situacao").asText()).isEqualTo("PAID");
        JsonNode payment = entry.get("baixas").get(0);
        assertThat(payment.get("formaPagamento").asText()).isEqualTo("BOLETO");
        assertThat(payment.get("multa").decimalValue()).isEqualByComparingTo("10.00"); // 2% de 500
        assertThat(payment.get("juros").decimalValue()).isEqualByComparingTo("3.40");
        assertThat(payment.get("total").decimalValue()).isEqualByComparingTo("513.40");
        assertThat(payment.get("dataPagamento").asText()).isEqualTo(paid.plusDays(1).toString()); // data do crédito

        JsonNode items = call("GET", base + "/billing/bank-returns/" + result.get("id").asText() + "/items", null, 200);
        assertThat(items.get(2).get("status").asText()).isEqualTo("INFO");
        assertThat(items.get(2).get("movementLabel").asText()).isEqualTo("Entrada confirmada");

        // Outro arquivo repetindo a mesma liquidação: não baixa de novo.
        String again = String.join("\r\n", header("756", today.minusDays(1)),
                segmentT(1, "06", "", String.valueOf(number), due, "500.00"),
                segmentU(2, "13.40", "0", "513.40", paid, paid.plusDays(1)), trailer());
        assertThat(upload(bank, again, 201).get("settled").asInt()).isZero();

        // Pendência "não localizada" é baixada manualmente na cobrança certa.
        JsonNode pending = call("GET", base + "/billing/bank-return-items/pending", null, 200);
        assertThat(pending).hasSize(2);
        String unknown = null;
        for (JsonNode p : pending) {
            if (p.get("yourNumber").asText().equals("99999999")) unknown = p.get("id").asText();
        }
        JsonNode settled = call("POST", base + "/billing/bank-return-items/" + unknown + "/settle",
                Map.of("entryId", second.get("id").asText()), 200);
        assertThat(settled.get("status").asText()).isEqualTo("SETTLED");
        assertThat(call("GET", base + "/financeiro/lancamentos/" + second.get("id").asText(), null, 200)
                .get("resumo").get("situacao").asText()).isEqualTo("PAID");
        call("POST", base + "/billing/bank-return-items/" + unknown + "/settle", Map.of("entryId", second.get("id").asText()), 422);
    }

    @Test
    void rejectsReturnFromAnotherBankAndOtherOrganizations() throws Exception {
        String bb = call("POST", base + "/financeiro/contas-bancarias", Map.of("nome", "Banco do Brasil", "tipo", "CHECKING", "codigoBanco", "001",
                "saldoInicial", "0", "dataAbertura", today.minusMonths(2).toString()), 201).get("id").asText();
        upload(bb, String.join("\n", header("756", today), trailer()), 422);

        String foreign = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/billing/bank-returns").header("Authorization", foreign)).andExpect(status().isNotFound());
    }
}
