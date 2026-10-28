package br.com.tekcondo.insight;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.condominium.entity.Condominium;
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

class InsightIT extends IntegrationTest {

    static final String ACC_FEE = "20000000-0000-0000-0000-000000000101";
    static final String ACC_WATER = "20000000-0000-0000-0000-000000000202";

    @Autowired
    ObjectMapper objectMapper;

    Condominium condo;
    String token;
    String base;
    String bank;
    LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void setUp() throws Exception {
        var org = data.organization();
        condo = data.condominium(org, "Condomínio Indicadores");
        token = TestData.bearer(data.login(data.orgAdmin(org)));
        base = "/api/v1/condominiums/" + condo.getId();
        bank = call("POST", base + "/finance/bank-accounts", Map.of("name", "Conta", "kind", "CHECKING", "openingBalance", "1000.00",
                "openingDate", today.minusYears(1).toString()), 201).get("id").asText();
    }

    private JsonNode call(String method, String url, Object body, int expected) throws Exception {
        var builder = "POST".equals(method) ? post(url) : get(url);
        builder.header("Authorization", token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private String entry(String kind, String account, String amount, LocalDate due) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("description", kind + " " + due);
        m.put("chartAccountId", account);
        m.put("amount", amount);
        m.put("dueDate", due.toString());
        return call("POST", base + "/finance/entries", m, 201).get(0).get("summary").get("id").asText();
    }

    private void paidExpense(String amount, LocalDate on) throws Exception {
        String id = entry("EXPENSE", ACC_WATER, amount, on);
        call("POST", base + "/finance/entries/" + id + "/approve", Map.of(), 200);
        call("POST", base + "/finance/entries/" + id + "/payments", Map.of("bankAccountId", bank, "paidOn", on.toString(),
                "principal", amount, "method", "PIX"), 200);
    }

    @Test
    void projectionUsesOpenDueDatesAndKeepsOverdueApart() throws Exception {
        entry("INCOME", ACC_FEE, "500.00", today.plusDays(2));
        entry("EXPENSE", ACC_WATER, "2000.00", today.plusDays(9));
        entry("INCOME", ACC_FEE, "300.00", today.minusDays(15));

        JsonNode p = call("GET", base + "/insights/cash-projection?weeks=4", null, 200);
        assertThat(p.get("currentBalance").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(p.get("overdueReceivables").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(p.get("weeks")).hasSize(4);
        assertThat(p.get("weeks").get(0).get("projectedBalance").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(p.get("lowestProjectedBalance").decimalValue()).isEqualByComparingTo("-500.00");
        assertThat(p.get("notes").toString()).contains("negativo");
    }

    @Test
    void flagsExpenseFarAboveHistory() throws Exception {
        LocalDate lastMonth = today.minusMonths(1).withDayOfMonth(5);
        for (int i = 1; i <= 6; i++) {
            paidExpense(i % 2 == 0 ? "1000.00" : "1040.00", lastMonth.minusMonths(i));
        }
        paidExpense("3100.00", lastMonth);

        JsonNode anomalies = call("GET", base + "/insights/expense-anomalies", null, 200);
        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).get("accountCode").asText()).isEqualTo("2.02");
        assertThat(anomalies.get(0).get("historicalMean").decimalValue()).isEqualByComparingTo("1020.00");
        assertThat(anomalies.get(0).get("month").asText()).isEqualTo(lastMonth.toString().substring(0, 7));
    }
}
