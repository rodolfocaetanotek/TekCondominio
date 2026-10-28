package br.com.tekcondo.finance;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.tekcondo.condominio.entity.Condominio;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceIT extends IntegrationTest {

    static final String ACC_MAINTENANCE = "20000000-0000-0000-0000-000000000205";
    static final String ACC_CONDO_FEE = "20000000-0000-0000-0000-000000000101";

    @Autowired
    ObjectMapper objectMapper;

    Administradora org;
    Condominio condo;
    String token;
    String base;
    String bank;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        org = data.administradora();
        condo = data.condominio(org, "Condomínio Financeiro");
        token = TestData.bearer(data.login(data.adminAdministradora(org)));
        base = "/api/v1/condominios/" + condo.getId() + "/finance";
        bank = call("POST", base + "/bank-accounts", Map.of("name", "Conta Corrente", "kind", "CHECKING",
                "openingBalance", "1000.00", "openingDate", today.minusMonths(6).toString()), 201).get("id").asText();
    }

    private JsonNode call(String method, String url, Object body, int expected) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        }
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private Map<String, Object> expense(String amount, String supplierId, String document) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", "EXPENSE");
        m.put("description", "Manutenção do portão");
        m.put("chartAccountId", ACC_MAINTENANCE);
        m.put("amount", amount);
        m.put("dueDate", today.plusDays(5).toString());
        m.put("supplierId", supplierId);
        m.put("documentNumber", document);
        return m;
    }

    private Map<String, Object> payment(String principal) {
        return Map.of("bankAccountId", bank, "paidOn", today.toString(), "principal", principal, "method", "PIX");
    }

    private String balance() throws Exception {
        for (JsonNode account : call("GET", base + "/bank-accounts", null, 200)) {
            if (account.get("id").asText().equals(bank)) {
                return account.get("balance").decimalValue().setScale(2).toPlainString();
            }
        }
        throw new AssertionError("conta não encontrada");
    }

    @Test
    void payableNeedsApprovalSupportsPartialPaymentsAndReversal() throws Exception {
        String supplier = call("POST", "/api/v1/suppliers", Map.of("name", "Portões Ltda", "document", "11.222.333/0001-81"), 201)
                .get("id").asText();
        JsonNode created = call("POST", base + "/entries", expense("500.00", supplier, "NF-123"), 201).get(0);
        String id = created.get("summary").get("id").asText();
        assertThat(created.get("summary").get("approval").asText()).isEqualTo("PENDING");

        call("POST", base + "/entries", expense("500.00", supplier, "NF-123"), 409); // mesma nota do mesmo fornecedor
        call("POST", base + "/entries/" + id + "/payments", payment("100.00"), 422); // sem aprovação
        call("POST", base + "/entries/" + id + "/approve", Map.of("note", "Ok"), 200);

        JsonNode partial = call("POST", base + "/entries/" + id + "/payments", payment("300.00"), 200);
        assertThat(partial.get("summary").get("status").asText()).isEqualTo("PARTIAL");
        assertThat(partial.get("summary").get("remaining").decimalValue()).isEqualByComparingTo("200.00");
        call("POST", base + "/entries/" + id + "/payments", payment("800.00"), 422);
        JsonNode paid = call("POST", base + "/entries/" + id + "/payments", payment("200.00"), 200);
        assertThat(paid.get("summary").get("status").asText()).isEqualTo("PAID");
        assertThat(balance()).isEqualTo("500.00");

        call("POST", base + "/entries/" + id + "/cancel", Map.of("reason", "Lançado errado"), 422); // possui baixas
        String paymentId = paid.get("payments").get(1).get("id").asText();
        call("POST", base + "/payments/" + paymentId + "/reverse", Map.of("reason", "x"), 400);
        JsonNode reversed = call("POST", base + "/payments/" + paymentId + "/reverse", Map.of("reason", "Pagamento devolvido pelo banco"), 200);
        assertThat(reversed.get("summary").get("status").asText()).isEqualTo("PARTIAL");
        assertThat(reversed.get("payments").get(1).get("reversed").asBoolean()).isTrue();
        assertThat(balance()).isEqualTo("700.00");
    }

    @Test
    void receivableQuoteAppliesFineAndInterestFromEntrySnapshot() throws Exception {
        String unit = call("POST", "/api/v1/condominios/" + condo.getId() + "/unidades",
                Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "10"), 201).get("id").asText();
        Map<String, Object> income = new HashMap<>();
        income.put("kind", "INCOME");
        income.put("description", "Taxa condominial");
        income.put("chartAccountId", ACC_CONDO_FEE);
        income.put("amount", "500.00");
        income.put("dueDate", today.minusDays(30).toString());
        income.put("unitId", unit);
        String id = call("POST", base + "/entries", income, 201).get(0).get("summary").get("id").asText();

        // Alterar o parâmetro depois não muda o título já emitido.
        call("PUT", base + "/settings", Map.of("finePercent", "1.00", "interestMonthlyPercent", "1.00",
                "billingDueDay", 10, "requirePayableApproval", true), 200);
        call("PUT", base + "/settings", Map.of("finePercent", "5.00", "interestMonthlyPercent", "1.00",
                "billingDueDay", 10, "requirePayableApproval", true), 400);

        JsonNode quote = call("GET", base + "/entries/" + id + "/quote?date=" + today, null, 200);
        assertThat(quote.get("daysLate").asInt()).isEqualTo(30);
        assertThat(quote.get("fine").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(quote.get("interest").decimalValue()).isEqualByComparingTo("5.00");

        Map<String, Object> pay = new HashMap<>(payment("500.00"));
        pay.put("fine", "10.00");
        pay.put("interest", "5.00");
        JsonNode paid = call("POST", base + "/entries/" + id + "/payments", pay, 200);
        assertThat(paid.get("payments").get(0).get("total").decimalValue()).isEqualByComparingTo("515.00");
        assertThat(balance()).isEqualTo("1515.00");

        mvc.perform(get(base + "/entries").header("Authorization", token).param("overdue", "true"))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void recurrenceCreatesMonthlyInstallmentsAndValidatesAccountNature() throws Exception {
        Map<String, Object> body = expense("150.00", null, null);
        body.put("repeat", 3);
        JsonNode list = call("POST", base + "/entries", body, 201);
        assertThat(list).hasSize(3);
        assertThat(list.get(2).get("summary").get("installmentLabel").asText()).isEqualTo("3/3");
        assertThat(list.get(2).get("summary").get("dueDate").asText()).isEqualTo(today.plusDays(5).plusMonths(2).toString());

        Map<String, Object> wrong = expense("10.00", null, null);
        wrong.put("chartAccountId", ACC_CONDO_FEE);
        call("POST", base + "/entries", wrong, 422);
        Map<String, Object> group = expense("10.00", null, null);
        group.put("chartAccountId", "20000000-0000-0000-0000-000000000002");
        call("POST", base + "/entries", group, 422);
    }

    @Test
    void transfersStatementAndReconciliation() throws Exception {
        String cash = call("POST", base + "/bank-accounts", Map.of("name", "Caixa", "kind", "CASH",
                "openingDate", today.minusMonths(1).toString()), 201).get("id").asText();
        call("POST", base + "/transfers", Map.of("fromAccountId", cash, "toAccountId", bank, "amount", "50.00",
                "date", today.toString()), 422); // caixa sem saldo
        String transfer = call("POST", base + "/transfers", Map.of("fromAccountId", bank, "toAccountId", cash, "amount", "200.00",
                "date", today.toString(), "description", "Suprimento de caixa"), 201).get("id").asText();

        JsonNode statement = call("GET", base + "/bank-accounts/" + bank + "/statement?from=" + today.withDayOfMonth(1), null, 200);
        assertThat(statement.get("openingBalance").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(statement.get("closingBalance").decimalValue()).isEqualByComparingTo("800.00");
        assertThat(statement.get("lines")).hasSize(1);

        call("POST", base + "/reconciliation", Map.of("items", List.of(Map.of("type", "TRANSFER_OUT", "id", transfer)),
                "reconciled", true), 200);
        call("POST", base + "/transfers/" + transfer + "/reverse", Map.of("reason", "Lançada em duplicidade"), 422);
        call("POST", base + "/reconciliation", Map.of("items", List.of(Map.of("type", "TRANSFER_OUT", "id", transfer)),
                "reconciled", false), 200);
        call("POST", base + "/transfers/" + transfer + "/reverse", Map.of("reason", "Lançada em duplicidade"), 200);
        assertThat(balance()).isIn("1000.00");
    }

    @Test
    void budgetComparesPlannedWithRealized() throws Exception {
        JsonNode entry = call("POST", base + "/entries", expense("120.00", null, null), 201).get(0);
        String id = entry.get("summary").get("id").asText();
        call("POST", base + "/entries/" + id + "/approve", null, 200);
        call("POST", base + "/entries/" + id + "/payments", payment("120.00"), 200);

        int year = today.getYear();
        JsonNode budget = call("PUT", base + "/budget/" + year, Map.of("cells", List.of(
                Map.of("chartAccountId", ACC_MAINTENANCE, "month", today.getMonthValue(), "amount", "300.00"))), 200);
        JsonNode row = null;
        for (JsonNode r : budget.get("rows")) {
            if (r.get("chartAccountId").asText().equals(ACC_MAINTENANCE)) row = r;
        }
        assertThat(row).isNotNull();
        assertThat(row.get("totalBudgeted").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(row.get("totalRealized").decimalValue()).isEqualByComparingTo("120.00");
    }

    @Test
    void rolesAndIsolation() throws Exception {
        JsonNode entry = call("POST", base + "/entries", expense("90.00", null, null), 201).get(0);
        String id = entry.get("summary").get("id").asText();

        Usuario syndic = data.usuario(org);
        data.conceder(syndic, "SYNDIC", condo.getId());
        String syndicToken = TestData.bearer(data.login(syndic));
        mvc.perform(post(base + "/entries/" + id + "/approve").header("Authorization", syndicToken))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/entries").header("Authorization", syndicToken).contentType(MediaType.APPLICATION_JSON)
                .content(data.json(expense("1.00", null, null)))).andExpect(status().isForbidden());
        mvc.perform(get(base + "/entries").header("Authorization", syndicToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conteudo", hasSize(1)));

        String foreign = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/entries/" + id).header("Authorization", foreign)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/bank-accounts").header("Authorization", foreign)).andExpect(status().isNotFound());
    }
}
