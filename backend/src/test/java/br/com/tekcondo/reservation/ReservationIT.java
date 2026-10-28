package br.com.tekcondo.reservation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.GravacaoEmailGateway;
import br.com.tekcondo.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    GravacaoEmailGateway mail;
    @Autowired
    JdbcTemplate jdbc;

    Administradora org;
    Condominio condo;
    String token;
    String base;
    String portal;
    String unit101;
    String unit102;
    String resident;
    LocalDate day = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(10);

    @BeforeEach
    void setUp() throws Exception {
        org = data.administradora();
        condo = data.condominio(org, "Condomínio Reservas");
        token = TestData.bearer(data.login(data.adminAdministradora(org)));
        base = "/api/v1/condominios/" + condo.getId();
        portal = "/api/v1/portal/" + condo.getId();
        unit101 = call(token, "POST", base + "/unidades", Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        unit102 = call(token, "POST", base + "/unidades", Map.of("identificacao", "102", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        String email = TestData.emailUnico();
        String person = call(token, "POST", "/api/v1/pessoas", Map.of("nome", "Rita Proprietária", "tipo", "INDIVIDUAL", "email", email), 201)
                .get("id").asText();
        call(token, "POST", base + "/vinculos", Map.of("idUnidade", unit101, "idPessoa", person, "tipo", "OWNER",
                "responsavelFinanceiro", true, "dataInicio", "2024-01-01"), 201);
        call(token, "POST", "/api/v1/pessoas/" + person + "/acesso-portal", null, 200);
        resident = activate(email);
    }

    private JsonNode call(String auth, String method, String url, Object body, int expected) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        if (!auth.isEmpty()) builder.header("Authorization", auth);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private String activate(String email) throws Exception {
        String invite = mail.ultimoTokenPara(email).orElseThrow();
        call("", "POST", "/api/v1/auth/senha/redefinir", Map.of("token", invite, "novaSenha", "MoradorSenha2026"), 204);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", email, "senha", "MoradorSenha2026")))).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
    }

    private String area(String name, String fee, boolean approval, boolean blockDelinquent, int minAdvanceHours, Integer maxPerMonth) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("fee", fee);
        m.put("requiresApproval", approval);
        m.put("blockDelinquent", blockDelinquent);
        m.put("openTime", "10:00");
        m.put("closeTime", "23:00");
        m.put("maxDurationMinutes", 600);
        m.put("minAdvanceHours", minAdvanceHours);
        m.put("maxAdvanceDays", 90);
        m.put("maxPerUnitMonth", maxPerMonth);
        return call(token, "POST", base + "/common-areas", m, 201).get("id").asText();
    }

    private Map<String, Object> booking(String area, String unit, LocalDate date, String start, String end) {
        return Map.of("areaId", area, "unitId", unit, "date", date.toString(), "startTime", start, "endTime", end, "guests", 40);
    }

    @Test
    void residentBooksWithApprovalFeeLimitsAndConflictProtection() throws Exception {
        String hall = area("Salão de festas", "150.00", true, true, 24, 1);

        call(resident, "POST", portal + "/reservations", booking(hall, unit102, day, "18:00", "22:00"), 403); // unidade de outro
        call(resident, "POST", portal + "/reservations", booking(hall, unit101, day, "09:00", "12:00"), 422); // fora do horário
        JsonNode pending = call(resident, "POST", portal + "/reservations", booking(hall, unit101, day, "18:00", "22:00"), 201);
        assertThat(pending.get("status").asText()).isEqualTo("PENDING");
        assertThat(pending.has("entryId")).isFalse();
        call(resident, "POST", portal + "/reservations", booking(hall, unit101, day.plusDays(1), "12:00", "14:00"), 422); // limite mensal
        call(token, "POST", base + "/reservations", booking(hall, unit102, day, "20:00", "23:00"), 409); // sobreposição

        JsonNode slots = call(resident, "GET", portal + "/common-areas/" + hall + "/occupancy?from=" + day + "&to=" + day, null, 200);
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).has("unitId")).isFalse(); // não expõe a unidade

        String id = pending.get("id").asText();
        call(token, "POST", base + "/reservations/" + id + "/reject", Map.of(), 400); // recusa exige motivo
        JsonNode confirmed = call(token, "POST", base + "/reservations/" + id + "/approve", Map.of("note", "Ok"), 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        String entry = confirmed.get("entryId").asText();
        JsonNode charge = call(token, "GET", base + "/finance/entries/" + entry, null, 200).get("summary");
        assertThat(charge.get("amount").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(charge.get("unitLabel").asText()).isEqualTo("101");
        assertThat(charge.get("personName").asText()).isEqualTo("Rita Proprietária");

        JsonNode cancelled = call(resident, "POST", portal + "/reservations/" + id + "/cancel", Map.of("reason", "Mudança de planos"), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(call(token, "GET", base + "/finance/entries/" + entry, null, 200).get("summary").get("status").asText()).isEqualTo("CANCELLED");

        // Inadimplente não reserva área com bloqueio.
        Map<String, Object> overdue = new HashMap<>();
        overdue.put("kind", "INCOME");
        overdue.put("description", "Taxa condominial");
        overdue.put("chartAccountId", "20000000-0000-0000-0000-000000000101");
        overdue.put("amount", "400.00");
        overdue.put("dueDate", LocalDate.now().minusDays(20).toString());
        overdue.put("unitId", unit101);
        call(token, "POST", base + "/finance/entries", overdue, 201);
        call(resident, "POST", portal + "/reservations", booking(hall, unit101, day, "18:00", "22:00"), 422);

        assertThat(call(resident, "GET", portal + "/reservations", null, 200)).hasSize(1);
    }

    @Test
    void advanceRulesApplyToResidentsAndDatabaseRejectsOverlaps() throws Exception {
        String grill = area("Churrasqueira", "0", false, false, 48, null);
        LocalDate tomorrow = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
        call(resident, "POST", portal + "/reservations", booking(grill, unit101, tomorrow, "12:00", "16:00"), 422);
        // A administração pode lançar com menos antecedência.
        call(token, "POST", base + "/reservations", booking(grill, unit102, tomorrow, "12:00", "16:00"), 201);
        JsonNode ok = call(resident, "POST", portal + "/reservations", booking(grill, unit101, day, "12:00", "16:00"), 201);
        assertThat(ok.get("status").asText()).isEqualTo("CONFIRMED");

        // Segunda barreira: mesmo burlando o serviço, o banco não aceita horário sobreposto.
        assertThatThrownBy(() -> jdbc.update("""
                insert into reservation (id, organization_id, condominium_id, area_id, unit_id, reservation_date, start_minute, end_minute,
                  status, requester_name, by_resident, created_at, updated_at)
                values (?, ?, ?, ?::uuid, ?::uuid, ?, 900, 1080, 'CONFIRMED', 'x', false, now(), now())
                """, UUID.randomUUID(), org.getId(), condo.getId(), grill, unit102, day)).isInstanceOf(DataIntegrityViolationException.class);

        String foreign = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        call(foreign, "GET", base + "/common-areas", null, 404);
        call(resident, "POST", base + "/common-areas", Map.of("name", "x", "openTime", "08:00", "closeTime", "09:00"), 403);
    }
}
