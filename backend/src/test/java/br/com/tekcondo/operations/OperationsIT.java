package br.com.tekcondo.operations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import br.com.tekcondo.announcement.AnnouncementRepository;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.jobs.DailyRemindersJob;
import br.com.tekcondo.notification.NotificationService;
import br.com.tekcondo.organization.entity.Organization;
import br.com.tekcondo.supplier.repository.ContractRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationsIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RecordingMailGateway mail;
    @Autowired
    ContractRepository contractRepository;
    @Autowired
    FinancialEntryRepository entryRepository;
    @Autowired
    AnnouncementRepository announcementRepository;
    @Autowired
    NotificationService notificationService;
    @Autowired
    TransactionTemplate tx;

    Organization org;
    Condominium condo;
    User admin;
    String token;
    String base;
    String unit101;
    String unit102;

    @BeforeEach
    void setUp() throws Exception {
        org = data.organization();
        condo = data.condominium(org, "Condomínio Operações");
        admin = data.orgAdmin(org);
        token = TestData.bearer(data.login(admin));
        base = "/api/v1/condominiums/" + condo.getId();
        unit101 = call("POST", base + "/units", Map.of("identifier", "101", "type", "APARTMENT", "idealFraction", "60"), 201).get("id").asText();
        unit102 = call("POST", base + "/units", Map.of("identifier", "102", "type", "APARTMENT", "idealFraction", "40"), 201).get("id").asText();
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

    @Test
    void documentsValidateContentAndAreServedOnlyThroughApi() throws Exception {
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        String id = objectMapper.readTree(mvc.perform(multipart(base + "/documents")
                        .file(new MockMultipartFile("file", "convencao.pdf", "application/pdf", pdf))
                        .param("category", "CONVENTION").param("title", "Convenção condominial").param("visibility", "RESIDENTS")
                        .header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(multipart(base + "/documents")
                        .file(new MockMultipartFile("file", "virus.pdf", "application/pdf", "MZ executável".getBytes()))
                        .param("category", "OTHER").param("title", "Falso PDF").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(base + "/documents")
                        .file(new MockMultipartFile("file", "script.exe", "application/octet-stream", new byte[]{1, 2}))
                        .param("category", "OTHER").param("title", "Executável").header("Authorization", token))
                .andExpect(status().isBadRequest());

        byte[] downloaded = mvc.perform(get(base + "/documents/" + id + "/download").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(pdf);

        String foreign = TestData.bearer(data.login(data.orgAdmin(data.organization())));
        mvc.perform(get(base + "/documents/" + id + "/download").header("Authorization", foreign)).andExpect(status().isNotFound());
        String storageKey = jdbc.queryForObject("select storage_key from document where id = ?::uuid", String.class, id);
        assertThat(storageKey).matches("[0-9a-f]{32}");
    }

    @Test
    void occurrenceLifecycleWithInteractionsAndStats() throws Exception {
        JsonNode created = call("POST", base + "/occurrences", Map.of("title", "Vazamento na garagem",
                "description", "Água escorrendo próximo à vaga 12", "category", "MAINTENANCE", "priority", "HIGH", "unitId", unit101), 201);
        String id = created.get("summary").get("id").asText();
        assertThat(created.get("summary").get("number").asInt()).isEqualTo(1);
        assertThat(call("POST", base + "/occurrences", Map.of("title", "Barulho", "description", "Festa até tarde",
                "category", "NOISE"), 201).get("summary").get("number").asInt()).isEqualTo(2);

        call("PUT", base + "/occurrences/" + id, Map.of("category", "MAINTENANCE", "priority", "URGENT",
                "assigneeName", "Zelador João", "dueDate", LocalDate.now().minusDays(1).toString()), 200);
        call("POST", base + "/occurrences/" + id + "/comments", Map.of("message", "Fornecedor acionado", "internal", true), 200);
        call("POST", base + "/occurrences/" + id + "/status", Map.of("status", "RESOLVED"), 400);
        JsonNode stats = call("GET", base + "/occurrences/stats", null, 200);
        assertThat(stats.get("overdue").asInt()).isEqualTo(1);

        JsonNode resolved = call("POST", base + "/occurrences/" + id + "/status", Map.of("status", "RESOLVED",
                "message", "Registro substituído"), 200);
        assertThat(resolved.get("resolution").asText()).isEqualTo("Registro substituído");
        assertThat(resolved.get("interactions")).hasSize(2);
        call("PUT", base + "/occurrences/" + id, Map.of("category", "MAINTENANCE", "priority", "LOW"), 422);
    }

    @Test
    void announcementReachesResidentsByNotificationAndEmail() throws Exception {
        String email = TestData.uniqueEmail();
        String person = call("POST", "/api/v1/people", Map.of("name", "Rita Moradora", "kind", "INDIVIDUAL", "email", email), 201)
                .get("id").asText();
        call("POST", base + "/occurrences", Map.of("title", "x", "description", "x", "category", "OTHER"), 201);
        call("POST", base + "/occurrences", Map.of("title", "y", "description", "y", "category", "OTHER"), 201);
        call("POST", base + "/occupancies", Map.of("unitId", unit101, "personId", person, "role", "RESIDENT",
                "financialResponsible", false, "startDate", "2025-01-01"), 201);
        call("POST", "/api/v1/people/" + person + "/portal-access", null, 200);
        String residentUser = jdbc.queryForObject("select id::text from app_user where person_id = ?::uuid", String.class, person);

        call("POST", base + "/announcements", Map.of("title", "Manutenção do elevador", "body", "O elevador ficará parado amanhã.",
                "audience", "UNIT", "unitId", unit102, "sendEmail", true), 201);
        assertThat(jdbc.queryForObject("select count(*) from notification where user_id = ?::uuid and title like 'Comunicado%'",
                Integer.class, residentUser)).isZero();

        call("POST", base + "/announcements", Map.of("title", "Assembleia em breve", "body", "Participe!", "audience", "ALL",
                "sendEmail", true, "pinned", true), 201);
        assertThat(jdbc.queryForObject("select count(*) from notification where user_id = ?::uuid and title like 'Comunicado%'",
                Integer.class, residentUser)).isEqualTo(1);
        assertThat(mail.to(email).stream().anyMatch(m -> m.subject().contains("Assembleia em breve"))).isTrue();

        JsonNode inbox = call("GET", "/api/v1/notifications", null, 200);
        assertThat(inbox.get("unread").asInt()).isGreaterThanOrEqualTo(1);
        call("POST", "/api/v1/notifications/read-all", null, 204);
        assertThat(call("GET", "/api/v1/notifications", null, 200).get("unread").asInt()).isZero();
    }

    @Test
    void assemblyFromCallToMinutesWithWeightedVotes() throws Exception {
        Instant when = Instant.now().plus(10, ChronoUnit.DAYS);
        JsonNode created = call("POST", base + "/assemblies", Map.of("title", "AGO 2026", "kind", "ORDINARY",
                "scheduledAt", when.toString(), "location", "Salão de festas", "voteWeight", "IDEAL_FRACTION",
                "agenda", List.of(Map.of("title", "Aprovação de contas", "voting", true),
                        Map.of("title", "Assuntos gerais", "voting", false))), 201);
        String id = created.get("id").asText();
        String item = created.get("agenda").get(0).get("id").asText();

        call("POST", base + "/assemblies/" + id + "/start", null, 422); // precisa estar convocada
        call("POST", base + "/assemblies/" + id + "/call", null, 200);
        assertThat(jdbc.queryForObject("select count(*) from announcement where condominium_id = ? and title like 'Convocação%'",
                Integer.class, condo.getId())).isEqualTo(1);
        call("POST", base + "/assemblies/" + id + "/start", null, 200);

        call("POST", base + "/assemblies/" + id + "/votes", Map.of("agendaItemId", item, "unitId", unit101, "choice", "YES"), 422);
        call("POST", base + "/assemblies/" + id + "/attendance", Map.of("unitId", unit101, "attendeeName", "Ana", "byProxy", false), 200);
        call("POST", base + "/assemblies/" + id + "/attendance", Map.of("unitId", unit102, "attendeeName", "Beto", "byProxy", true), 400);
        JsonNode withProxy = call("POST", base + "/assemblies/" + id + "/attendance", Map.of("unitId", unit102, "attendeeName", "Beto",
                "byProxy", true, "proxyName", "Carlos Procurador"), 200);
        assertThat(withProxy.get("quorum").get("fractionPresent").decimalValue()).isEqualByComparingTo("100");

        call("POST", base + "/assemblies/" + id + "/votes", Map.of("agendaItemId", item, "unitId", unit101, "choice", "YES"), 200);
        call("POST", base + "/assemblies/" + id + "/votes", Map.of("agendaItemId", item, "unitId", unit101, "choice", "NO"), 409);
        JsonNode voted = call("POST", base + "/assemblies/" + id + "/votes", Map.of("agendaItemId", item, "unitId", unit102, "choice", "NO"), 200);
        JsonNode tally = voted.get("agenda").get(0).get("tally");
        assertThat(tally.get("yes").decimalValue()).isEqualByComparingTo("60");
        assertThat(tally.get("no").decimalValue()).isEqualByComparingTo("40");

        call("PUT", base + "/assemblies/" + id + "/agenda/" + item + "/deliberation", Map.of("deliberation", "Contas aprovadas por maioria",
                "responsible", "Síndico", "deadline", LocalDate.now().plusDays(30).toString()), 200);
        String draft = call("GET", base + "/assemblies/" + id + "/minutes-draft", null, 200).get("minutes").asText();
        assertThat(draft).contains("Contas aprovadas por maioria").contains("favoráveis 60").contains("2 de 2 unidades");
        call("PUT", base + "/assemblies/" + id + "/minutes", Map.of("minutes", draft), 200);
        assertThat(call("POST", base + "/assemblies/" + id + "/close", null, 200).get("status").asText()).isEqualTo("CLOSED");
        call("POST", base + "/assemblies/" + id + "/votes", Map.of("agendaItemId", item, "unitId", unit102, "choice", "YES"), 422);
    }

    @Test
    void contractsHistoryExpirationAndReminderJob() throws Exception {
        String supplier = call("POST", "/api/v1/suppliers", Map.of("name", "Elevadores Sobe Desce"), 201).get("id").asText();
        JsonNode created = call("POST", base + "/contracts", Map.of("supplierId", supplier, "title", "Manutenção de elevadores",
                "amount", "1200.00", "periodicity", "MONTHLY", "startDate", LocalDate.now().minusYears(1).toString(),
                "endDate", LocalDate.now().plusDays(10).toString(), "readjustmentIndex", "IPCA", "noticeDays", 30), 201);
        assertThat(created.get("expiring").asBoolean()).isTrue();
        String id = created.get("id").asText();
        JsonNode updated = call("PUT", base + "/contracts/" + id, Map.of("supplierId", supplier, "title", "Manutenção de elevadores",
                "amount", "1290.00", "periodicity", "MONTHLY", "startDate", LocalDate.now().minusYears(1).toString(),
                "endDate", LocalDate.now().plusDays(10).toString(), "version", created.get("version").asLong()), 200);
        assertThat(updated.get("history").get(0).get("change").asText()).contains("1200.00 → 1290.00");

        DailyRemindersJob job = new DailyRemindersJob(contractRepository, entryRepository, announcementRepository,
                notificationService, Clock.systemUTC());
        tx.executeWithoutResult(s -> job.run());
        tx.executeWithoutResult(s -> job.run()); // não duplica
        assertThat(jdbc.queryForObject("select count(*) from notification where user_id = ? and title like 'Contrato a vencer%'",
                Integer.class, admin.getId())).isEqualTo(1);

        call("POST", base + "/contracts/" + id + "/terminate", Map.of("terminatedOn", LocalDate.now().toString(), "reason", "Troca de fornecedor"), 200);
        assertThat(call("GET", base + "/contracts", null, 200).get(0).get("status").asText()).isEqualTo("TERMINATED");
    }
}
