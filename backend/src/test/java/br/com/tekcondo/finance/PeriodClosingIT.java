package br.com.tekcondo.finance;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.organization.entity.Organization;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeriodClosingIT extends IntegrationTest {

    static final String ACC_CONDO_FEE = "20000000-0000-0000-0000-000000000101";

    @Autowired
    ObjectMapper objectMapper;

    Organization org;
    Condominium condo;
    String token;
    String base;
    String bank;
    String cash;
    LocalDate today = LocalDate.now();
    LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);
    LocalDate lastMonthDay = lastMonthEnd.withDayOfMonth(10);

    @BeforeEach
    void setUp() throws Exception {
        org = data.organization();
        condo = data.condominium(org, "Condomínio Fechamento");
        token = TestData.bearer(data.login(data.orgAdmin(org)));
        base = "/api/v1/condominiums/" + condo.getId() + "/finance";
        bank = call("POST", base + "/bank-accounts", Map.of("name", "Conta Corrente", "kind", "CHECKING",
                "openingBalance", "1000.00", "openingDate", today.minusMonths(6).toString()), 201).get("id").asText();
        cash = call("POST", base + "/bank-accounts", Map.of("name", "Caixa", "kind", "CASH",
                "openingBalance", "0", "openingDate", today.minusMonths(6).toString()), 201).get("id").asText();
    }

    private JsonNode call(String method, String url, Object body, int expected) throws Exception {
        return call(token, method, url, body, expected);
    }

    private JsonNode call(String auth, String method, String url, Object body, int expected) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", auth);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        }
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private Map<String, Object> income(String amount, LocalDate due) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", "INCOME");
        m.put("description", "Receita avulsa");
        m.put("chartAccountId", ACC_CONDO_FEE);
        m.put("amount", amount);
        m.put("dueDate", due.toString());
        return m;
    }

    private Map<String, Object> payment(LocalDate paidOn, String principal) {
        return Map.of("bankAccountId", bank, "paidOn", paidOn.toString(), "principal", principal, "method", "PIX");
    }

    @Test
    void closingFreezesThePeriodUntilReopenedWithJustification() throws Exception {
        String first = call("POST", base + "/entries", income("300.00", lastMonthDay), 201).get(0).get("summary").get("id").asText();
        JsonNode paid = call("POST", base + "/entries/" + first + "/payments", payment(lastMonthDay, "300.00"), 200);
        String paymentId = paid.get("payments").get(0).get("id").asText();
        String second = call("POST", base + "/entries", income("200.00", lastMonthDay), 201).get(0).get("summary").get("id").asText();

        JsonNode status = call("GET", base + "/closing", null, 200);
        assertThat(status.get("suggestedUntil").asText()).isEqualTo(lastMonthEnd.toString());
        assertThat(status.has("closedUntil")).isFalse();

        JsonNode check = call("GET", base + "/closing/check?until=" + lastMonthEnd, null, 200);
        assertThat(check.get("unreconciledMovements").asLong()).isEqualTo(1);
        assertThat(check.get("income").decimalValue()).isEqualByComparingTo("300.00");

        // Somente fim de mês e somente meses encerrados.
        call("POST", base + "/closing", Map.of("until", lastMonthEnd.minusDays(3).toString()), 400);
        LocalDate thisMonthEnd = today.withDayOfMonth(today.lengthOfMonth());
        call("POST", base + "/closing", Map.of("until", thisMonthEnd.toString()), 422);

        // Funcionário da administradora não fecha período.
        User staff = data.user(org);
        data.grant(staff, "ORG_STAFF", null);
        call(TestData.bearer(data.login(staff)), "POST", base + "/closing", Map.of("until", lastMonthEnd.toString()), 403);

        JsonNode closed = call("POST", base + "/closing", Map.of("until", lastMonthEnd.toString(), "notes", "Conferido"), 200);
        assertThat(closed.get("closedUntil").asText()).isEqualTo(lastMonthEnd.toString());
        JsonNode snapshot = closed.get("history").get(0).get("snapshot");
        assertThat(snapshot.get("income").asText()).isEqualTo("300.00");
        assertThat(snapshot.get("balances")).hasSize(2);
        call("POST", base + "/closing", Map.of("until", lastMonthEnd.toString()), 409);

        // Nada retroativo: baixa, estorno, lançamento, transferência e conciliação no período fechado.
        call("POST", base + "/entries/" + second + "/payments", payment(lastMonthDay, "200.00"), 422);
        call("POST", base + "/payments/" + paymentId + "/reverse", Map.of("reason", "Devolução bancária"), 422);
        call("POST", base + "/entries", income("50.00", lastMonthDay), 422);
        call("POST", base + "/entries/" + second + "/cancel", Map.of("reason", "Lançado em duplicidade"), 422);
        call("POST", base + "/transfers", Map.of("fromAccountId", bank, "toAccountId", cash, "amount", "10.00",
                "date", lastMonthDay.toString()), 422);
        call("POST", base + "/reconciliation", Map.of("reconciled", true,
                "items", java.util.List.of(Map.of("type", "PAYMENT", "id", paymentId))), 422);

        // O período aberto continua operando normalmente.
        call("POST", base + "/entries/" + second + "/payments", payment(today, "200.00"), 200);
        call("POST", base + "/entries", income("80.00", today), 201);

        call("POST", base + "/closing/reopen", Map.of("reason", "x"), 400);
        JsonNode reopened = call("POST", base + "/closing/reopen", Map.of("reason", "Estorno solicitado pelo banco"), 200);
        assertThat(reopened.has("closedUntil")).isFalse();
        assertThat(reopened.get("history")).hasSize(2);
        assertThat(reopened.get("history").get(0).get("action").asText()).isEqualTo("REOPEN");
        call("POST", base + "/payments/" + paymentId + "/reverse", Map.of("reason", "Devolução bancária"), 200);
    }

    @Test
    void closingIsIsolatedPerOrganization() throws Exception {
        String foreign = TestData.bearer(data.login(data.orgAdmin(data.organization())));
        call(foreign, "GET", base + "/closing", null, 404);
        call(foreign, "POST", base + "/closing", Map.of("until", lastMonthEnd.toString()), 404);
    }
}
