package br.com.tekcondo.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingIT extends IntegrationTest {

    static final String ACC_CONDO_FEE = "20000000-0000-0000-0000-000000000101";
    static final String ACC_RESERVE = "20000000-0000-0000-0000-000000000102";

    @Autowired
    ObjectMapper objectMapper;

    Administradora org;
    Condominium condo;
    String token;
    String base;
    String unitA;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        org = data.administradora();
        condo = data.condominium(org, "Condomínio Cobrança");
        token = TestData.bearer(data.login(data.adminAdministradora(org)));
        base = "/api/v1/condominiums/" + condo.getId();
        unitA = call("POST", base + "/units", Map.of("identifier", "A", "type", "APARTMENT", "idealFraction", "50"), 201).get("id").asText();
        call("POST", base + "/units", Map.of("identifier", "B", "type", "APARTMENT", "idealFraction", "30"), 201);
        call("POST", base + "/units", Map.of("identifier", "C", "type", "APARTMENT", "idealFraction", "20"), 201);
        String person = call("POST", "/api/v1/people", Map.of("name", "Ana Pagadora", "kind", "INDIVIDUAL"), 201).get("id").asText();
        call("POST", base + "/occupancies", Map.of("unitId", unitA, "personId", person, "role", "OWNER",
                "financialResponsible", true, "startDate", "2024-01-01"), 201);
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

    private Map<String, Object> allocation(YearMonth competence, String account, LocalDate due) {
        Map<String, Object> m = new HashMap<>();
        m.put("competence", competence.toString());
        m.put("description", "Taxa condominial " + competence);
        m.put("chartAccountId", account);
        m.put("method", "IDEAL_FRACTION");
        m.put("amount", "1000.00");
        m.put("dueDate", due == null ? null : due.toString());
        return m;
    }

    @Test
    void previewGenerateDuplicateAndCancel() throws Exception {
        YearMonth next = YearMonth.from(today).plusMonths(1);
        JsonNode preview = call("POST", base + "/billing/allocations/preview", allocation(next, ACC_CONDO_FEE, null), 200);
        assertThat(preview.get("total").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(preview.get("unitsWithoutResponsible").asInt()).isEqualTo(2);
        assertThat(preview.get("dueDate").asText()).isEqualTo(next.atDay(10).toString());
        assertThat(preview.get("lines").get(0).get("amount").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(preview.get("lines").get(0).get("responsibleName").asText()).isEqualTo("Ana Pagadora");

        String id = call("POST", base + "/billing/allocations", allocation(next, ACC_CONDO_FEE, null), 201).get("id").asText();
        call("POST", base + "/billing/allocations", allocation(next, ACC_CONDO_FEE, null), 409);
        call("POST", base + "/billing/allocations", allocation(next, ACC_RESERVE, null), 201); // outra conta, mesma competência

        JsonNode charges = call("GET", base + "/finance/entries?kind=INCOME&unitId=" + unitA, null, 200);
        assertThat(charges.get("totalElementos").asInt()).isEqualTo(2);
        assertThat(charges.get("conteudo").get(0).get("personName").asText()).isEqualTo("Ana Pagadora");

        call("POST", base + "/billing/allocations/" + id + "/cancel", Map.of("reason", "Valor aprovado em assembleia mudou"), 200);
        call("POST", base + "/billing/allocations", allocation(next, ACC_CONDO_FEE, null), 201);
    }

    @Test
    void idealFractionRequiresBalancedUnits() throws Exception {
        call("POST", base + "/units", Map.of("identifier", "D", "type", "STORE", "idealFraction", "5"), 201);
        call("POST", base + "/billing/allocations/preview", allocation(YearMonth.from(today), ACC_CONDO_FEE, null), 422);
        Map<String, Object> equal = allocation(YearMonth.from(today), ACC_CONDO_FEE, null);
        equal.put("method", "EQUAL");
        JsonNode preview = call("POST", base + "/billing/allocations/preview", equal, 200);
        assertThat(preview.get("lines").get(0).get("amount").decimalValue()).isEqualByComparingTo("250.00");
    }

    @Test
    void delinquencyDebtStatementAgreementAndCancellation() throws Exception {
        YearMonth past = YearMonth.from(today).minusMonths(2);
        call("POST", base + "/billing/allocations", allocation(past, ACC_CONDO_FEE, today.minusDays(60)), 201);

        JsonNode report = call("GET", base + "/billing/delinquency", null, 200);
        assertThat(report.get("unitsInDebt").asInt()).isEqualTo(3);
        assertThat(report.get("totalPrincipal").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(report.get("totalCharges").decimalValue()).isEqualByComparingTo("40.00"); // multa 2% (20) + juros 2 meses (20)
        assertThat(report.get("delinquencyRate").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(report.get("rows").get(0).get("unitLabel").asText()).isEqualTo("A");

        call("POST", base + "/billing/units/" + unitA + "/notes", Map.of("contactType", "PHONE",
                "contactDate", today.toString(), "note", "Prometeu pagar na sexta"), 201);
        JsonNode debt = call("GET", base + "/billing/units/" + unitA + "/debt", null, 200);
        assertThat(debt.get("openTotal").decimalValue()).isEqualByComparingTo("520.00");
        assertThat(debt.get("notes").get(0).get("note").asText()).isEqualTo("Prometeu pagar na sexta");
        String charge = debt.get("lines").get(0).get("entryId").asText();

        Map<String, Object> agreement = new HashMap<>();
        agreement.put("unitId", unitA);
        agreement.put("entryIds", List.of(charge));
        agreement.put("installments", 3);
        agreement.put("firstDueDate", today.plusDays(10).toString());
        agreement.put("discount", "10.00");
        agreement.put("includeCharges", true);
        JsonNode created = call("POST", base + "/billing/agreements", agreement, 201);
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("510.00");
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode parcel : created.get("installmentEntries")) {
            sum = sum.add(parcel.get("amount").decimalValue());
        }
        assertThat(sum).isEqualByComparingTo("510.00");
        call("POST", base + "/billing/agreements", agreement, 422); // débito já renegociado

        report = call("GET", base + "/billing/delinquency", null, 200);
        assertThat(report.get("unitsInDebt").asInt()).isEqualTo(2);

        call("POST", base + "/billing/agreements/" + created.get("id").asText() + "/cancel",
                Map.of("reason", "Condômino desistiu do acordo"), 200);
        report = call("GET", base + "/billing/delinquency", null, 200);
        assertThat(report.get("unitsInDebt").asInt()).isEqualTo(3);
    }

    @Test
    void councilCanViewButNotGenerate() throws Exception {
        Usuario council = data.usuario(org);
        data.conceder(council, "COUNCIL", condo.getId());
        String councilToken = TestData.bearer(data.login(council));
        mvc.perform(get(base + "/billing/delinquency").header("Authorization", councilToken)).andExpect(status().isOk());
        mvc.perform(post(base + "/billing/allocations").header("Authorization", councilToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(allocation(YearMonth.from(today), ACC_CONDO_FEE, null))))
                .andExpect(status().isForbidden());
    }
}
