package br.com.tekcondo.visitor;

import java.time.LocalDate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VisitorIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    RecordingMailGateway mail;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    VisitorService visitorService;

    Organization org;
    Condominium condo;
    String admin;
    String porter;
    String resident;
    String base;
    String portal;
    String unit101;
    String unit102;

    @BeforeEach
    void setUp() throws Exception {
        org = data.organization();
        condo = data.condominium(org, "Condomínio Portaria");
        admin = TestData.bearer(data.login(data.orgAdmin(org)));
        User staff = data.user(org);
        data.grant(staff, "CONDO_STAFF", condo.getId());
        porter = TestData.bearer(data.login(staff));
        base = "/api/v1/condominiums/" + condo.getId();
        portal = "/api/v1/portal/" + condo.getId();
        unit101 = call(admin, "POST", base + "/units", Map.of("identifier", "101", "type", "APARTMENT", "idealFraction", "50"), 201).get("id").asText();
        unit102 = call(admin, "POST", base + "/units", Map.of("identifier", "102", "type", "APARTMENT", "idealFraction", "50"), 201).get("id").asText();
        String email = TestData.uniqueEmail();
        String person = call(admin, "POST", "/api/v1/people", Map.of("name", "Caio Morador", "kind", "INDIVIDUAL", "email", email), 201)
                .get("id").asText();
        call(admin, "POST", base + "/occupancies", Map.of("unitId", unit101, "personId", person, "role", "TENANT",
                "financialResponsible", false, "startDate", "2024-01-01"), 201);
        call(admin, "POST", "/api/v1/people/" + person + "/portal-access", null, 200);
        resident = activate(email);
    }

    private JsonNode call(String auth, String method, String url, Object body, int expected) throws Exception {
        var builder = "POST".equals(method) ? post(url) : get(url);
        if (!auth.isEmpty()) builder.header("Authorization", auth);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private String activate(String email) throws Exception {
        String invite = mail.lastTokenFor(email).orElseThrow();
        call("", "POST", "/api/v1/auth/password/reset", Map.of("token", invite, "newPassword", "MoradorSenha2026"), 204);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", email, "password", "MoradorSenha2026")))).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
    }

    @Test
    void residentPreAuthorizesAndGateChecksInByCode() throws Exception {
        call(resident, "POST", portal + "/visitors", Map.of("unitId", unit102, "kind", "VISITOR", "name", "Fulano"), 403);
        JsonNode pass = call(resident, "POST", portal + "/visitors", Map.of("unitId", unit101, "kind", "SERVICE_PROVIDER",
                "name", "Pedro Eletricista", "document", "123.456.789-09", "company", "Luz Ltda", "vehiclePlate", "ABC-1D23"), 201);
        String code = pass.get("accessCode").asText();
        assertThat(code).matches("\\d{6}");
        assertThat(pass.get("document").asText()).isEqualTo("********909");
        assertThat(pass.get("vehiclePlate").asText()).isEqualTo("ABC1D23");

        JsonNode today = call(porter, "GET", base + "/visitors/today", null, 200);
        assertThat(today).hasSize(1);
        assertThat(today.get(0).has("accessCode")).isFalse(); // a portaria não vê o código

        call(porter, "POST", base + "/visitors/check-in-code", Map.of("code", "12345"), 400);
        JsonNode inside = call(porter, "POST", base + "/visitors/check-in-code", Map.of("code", code), 200);
        assertThat(inside.get("status").asText()).isEqualTo("INSIDE");
        call(porter, "POST", base + "/visitors/check-in-code", Map.of("code", code), 404); // código de uso único

        JsonNode inbox = call(resident, "GET", "/api/v1/notifications", null, 200);
        assertThat(inbox.get("items").get(0).get("title").asText()).isEqualTo("Prestador de serviço na portaria");

        JsonNode left = call(porter, "POST", base + "/visitors/" + pass.get("id").asText() + "/check-out", null, 200);
        assertThat(left.get("status").asText()).isEqualTo("LEFT");

        // Chegada sem aviso: registrada pela portaria com entrada imediata.
        JsonNode walkIn = call(porter, "POST", base + "/visitors/arrivals", Map.of("unitId", unit102, "kind", "DELIVERY", "name", "Entregador"), 201);
        assertThat(walkIn.get("status").asText()).isEqualTo("INSIDE");
        assertThat(call(resident, "GET", portal + "/visitors", null, 200)).hasSize(1); // morador só vê a própria unidade
    }

    @Test
    void permissionsIsolationAndRetention() throws Exception {
        User council = data.user(org);
        data.grant(council, "COUNCIL", condo.getId());
        String councilToken = TestData.bearer(data.login(council));
        call(councilToken, "GET", base + "/visitors/today", null, 200);
        call(councilToken, "POST", base + "/visitors/arrivals", Map.of("unitId", unit101, "kind", "VISITOR", "name", "X"), 403);
        call(TestData.bearer(data.login(data.orgAdmin(data.organization()))), "GET", base + "/visitors/today", null, 404);

        String id = call(resident, "POST", portal + "/visitors", Map.of("unitId", unit101, "kind", "VISITOR", "name", "Maria Visita",
                "document", "MG1234567"), 201).get("id").asText();
        call(resident, "POST", portal + "/visitors/" + id + "/cancel", null, 200);
        jdbc.update("update visitor_pass set valid_from = ?, valid_until = ? where id = ?::uuid",
                LocalDate.now().minusDays(400), LocalDate.now().minusDays(400), id);
        assertThat(visitorService.anonymizeOlderThan(180)).isGreaterThanOrEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("select name, document, anonymized_at from visitor_pass where id = ?::uuid", id);
        assertThat(row.get("name")).isEqualTo("[anonimizado]");
        assertThat(row.get("document")).isNull();
        assertThat(row.get("anonymized_at")).isNotNull();
    }
}
