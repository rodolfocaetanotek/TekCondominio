package br.com.tekcondo.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.organization.entity.Organization;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.RecordingMailGateway;
import br.com.tekcondo.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportsDashboardPortalIT extends IntegrationTest {

    static final String ACC_CONDO_FEE = "20000000-0000-0000-0000-000000000101";
    static final String ACC_MAINTENANCE = "20000000-0000-0000-0000-000000000205";

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RecordingMailGateway mail;

    Organization org;
    Condominium condo;
    String token;
    String base;
    String bank;
    String unitOwner;
    String unitOther;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        org = data.organization();
        condo = data.condominium(org, "Condomínio Relatórios");
        token = TestData.bearer(data.login(data.orgAdmin(org)));
        base = "/api/v1/condominiums/" + condo.getId();
        call(token, "PUT", base + "/finance/settings", Map.of("finePercent", "2", "interestMonthlyPercent", "1",
                "billingDueDay", 10, "requirePayableApproval", false), 200);
        bank = call(token, "POST", base + "/finance/bank-accounts", Map.of("name", "Banco", "kind", "CHECKING",
                "openingBalance", "1000.00", "openingDate", today.minusYears(1).toString()), 201).get("id").asText();
        unitOwner = call(token, "POST", base + "/units", Map.of("identifier", "11", "type", "APARTMENT", "idealFraction", "50"), 201).get("id").asText();
        unitOther = call(token, "POST", base + "/units", Map.of("identifier", "12", "type", "APARTMENT", "idealFraction", "50"), 201).get("id").asText();

        Map<String, Object> expense = new HashMap<>(Map.of("kind", "EXPENSE", "description", "Conserto bomba",
                "chartAccountId", ACC_MAINTENANCE, "amount", "300.00", "dueDate", today.toString()));
        String exp = call(token, "POST", base + "/finance/entries", expense, 201).get(0).get("summary").get("id").asText();
        call(token, "POST", base + "/finance/entries/" + exp + "/payments", Map.of("bankAccountId", bank,
                "paidOn", today.toString(), "principal", "300.00", "method", "PIX"), 200);
        call(token, "POST", base + "/billing/allocations", Map.of("competence", YearMonth.from(today).toString(),
                "description", "Taxa " + YearMonth.from(today), "chartAccountId", ACC_CONDO_FEE, "method", "IDEAL_FRACTION",
                "amount", "800.00", "dueDate", today.plusDays(5).toString()), 201);
    }

    private JsonNode call(String auth, String method, String url, Object body, int expected) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", auth);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    @Test
    void everyReportRendersAndExportsInAllFormats() throws Exception {
        for (ReportService.Kind kind : ReportService.Kind.values()) {
            String url = base + "/reports/" + kind + "?from=" + today.withDayOfMonth(1) + "&to=" + today.plusDays(10)
                    + (kind == ReportService.Kind.BANK_MOVEMENT ? "&bankAccountId=" + bank : "");
            JsonNode report = call(token, "GET", url, null, 200);
            assertThat(report.get("title").asText()).as(kind.name()).isNotBlank();
        }
        JsonNode ie = call(token, "GET", base + "/reports/INCOME_EXPENSE", null, 200);
        assertThat(ie.get("totals").get(3).decimalValue()).isEqualByComparingTo("-300.00");

        byte[] csv = mvc.perform(get(base + "/reports/PAYABLES/export").param("format", "CSV").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Vencimento;").contains("300,00");
        byte[] xlsx = mvc.perform(get(base + "/reports/CASH_FLOW/export").param("format", "XLSX").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        byte[] pdf = mvc.perform(get(base + "/reports/DELINQUENCY/export").param("format", "PDF").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'REPORT_EXPORTED' and entity_id = ?",
                Integer.class, condo.getId().toString())).isEqualTo(3);

        User staff = data.user(org);
        data.grant(staff, "CONDO_STAFF", condo.getId());
        mvc.perform(get(base + "/reports/INCOME_EXPENSE").header("Authorization", TestData.bearer(data.login(staff))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardAggregatesOnlyAccessibleCondominiums() throws Exception {
        JsonNode d = call(token, "GET", "/api/v1/dashboard", null, 200);
        assertThat(d.get("condominiums").asInt()).isEqualTo(1);
        assertThat(d.get("units").asInt()).isEqualTo(2);
        assertThat(d.get("expense").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(d.get("balance").decimalValue()).isEqualByComparingTo("700.00");
        assertThat(d.get("receivablesOpen").decimalValue()).isEqualByComparingTo("800.00");
        assertThat(d.get("chargesIssued").asInt()).isEqualTo(2);
        assertThat(d.get("series")).hasSize(12);

        Condominium other = data.condominium(org, "Outro");
        User syndic = data.user(org);
        data.grant(syndic, "SYNDIC", other.getId());
        JsonNode scoped = call(TestData.bearer(data.login(syndic)), "GET", "/api/v1/dashboard", null, 200);
        assertThat(scoped.get("condominiums").asInt()).isEqualTo(1);
        assertThat(scoped.get("units").asInt()).isZero();
        call(TestData.bearer(data.login(syndic)), "GET", "/api/v1/dashboard?condominiumId=" + condo.getId(), null, 404);
    }

    @Test
    void residentPortalShowsOnlyOwnData() throws Exception {
        String ownerEmail = TestData.uniqueEmail();
        String owner = call(token, "POST", "/api/v1/people", Map.of("name", "Olga Dona", "kind", "INDIVIDUAL", "email", ownerEmail), 201).get("id").asText();
        String tenantEmail = TestData.uniqueEmail();
        String tenant = call(token, "POST", "/api/v1/people", Map.of("name", "Ivo Morador", "kind", "INDIVIDUAL", "email", tenantEmail), 201).get("id").asText();
        call(token, "POST", base + "/occupancies", Map.of("unitId", unitOwner, "personId", owner, "role", "OWNER",
                "financialResponsible", true, "startDate", "2024-01-01"), 201);
        call(token, "POST", base + "/occupancies", Map.of("unitId", unitOwner, "personId", tenant, "role", "RESIDENT",
                "financialResponsible", false, "startDate", "2024-01-01"), 201);
        call(token, "POST", "/api/v1/people/" + owner + "/portal-access", null, 200);
        call(token, "POST", "/api/v1/people/" + tenant + "/portal-access", null, 200);
        String ownerToken = activate(ownerEmail);
        String tenantToken = activate(tenantEmail);

        JsonNode me = call(ownerToken, "GET", "/api/v1/portal/me", null, 200);
        assertThat(me.get("condominiums").get(0).get("units").get(0).get("label").asText()).isEqualTo("11");
        String p = "/api/v1/portal/" + condo.getId();
        JsonNode charges = call(ownerToken, "GET", p + "/charges", null, 200);
        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).get("openTotal").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(call(tenantToken, "GET", p + "/charges", null, 200)).isEmpty();

        byte[] pdf = "%PDF-1.4 regulamento".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart(base + "/documents").file(new MockMultipartFile("file", "regulamento.pdf", "application/pdf", pdf))
                .param("category", "INTERNAL_RULES").param("title", "Regulamento").param("visibility", "RESIDENTS")
                .header("Authorization", token)).andExpect(status().isCreated());
        mvc.perform(multipart(base + "/documents").file(new MockMultipartFile("file", "folha.pdf", "application/pdf", pdf))
                .param("category", "OTHER").param("title", "Folha de pagamento").header("Authorization", token)).andExpect(status().isCreated());
        JsonNode docs = call(tenantToken, "GET", p + "/documents", null, 200);
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).get("title").asText()).isEqualTo("Regulamento");

        String occ = call(tenantToken, "POST", p + "/occurrences", Map.of("title", "Lâmpada queimada", "description", "Corredor do 1º andar",
                "category", "MAINTENANCE", "priority", "URGENT", "unitId", unitOwner), 201).get("summary").get("id").asText();
        call(tenantToken, "POST", p + "/occurrences", Map.of("title", "x", "description", "x", "category", "OTHER", "unitId", unitOther), 403);
        call(token, "POST", base + "/occurrences/" + occ + "/comments", Map.of("message", "Nota interna da equipe", "internal", true), 200);
        call(token, "POST", base + "/occurrences/" + occ + "/comments", Map.of("message", "Eletricista agendado", "internal", false), 200);
        JsonNode seen = call(tenantToken, "GET", p + "/occurrences/" + occ, null, 200);
        assertThat(seen.get("summary").get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(seen.get("interactions")).hasSize(1);
        assertThat(seen.get("interactions").get(0).get("message").asText()).isEqualTo("Eletricista agendado");

        assertThat(call(ownerToken, "GET", p + "/calendar", null, 200).get(0).get("type").asText()).isEqualTo("CHARGE");
        call(token, "GET", "/api/v1/portal/me", null, 403); // administrador não é morador
        call(ownerToken, "GET", base + "/finance/entries", null, 403); // morador não acessa a área administrativa
    }

    private String activate(String email) throws Exception {
        String invite = mail.lastTokenFor(email).orElseThrow();
        call("", "POST", "/api/v1/auth/password/reset", Map.of("token", invite, "newPassword", "MoradorSenha2026"), 204);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", email, "password", "MoradorSenha2026")))).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
    }
}
