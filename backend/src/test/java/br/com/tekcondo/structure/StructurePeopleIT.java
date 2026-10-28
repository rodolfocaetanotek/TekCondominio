package br.com.tekcondo.structure;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.GravacaoEmailGateway;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StructurePeopleIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    GravacaoEmailGateway mail;

    Administradora org;
    Condominium condo;
    String token;
    String base;

    @BeforeEach
    void setUp() {
        org = data.administradora();
        condo = data.condominium(org, "Residencial Jatobá");
        token = TestData.bearer(data.login(data.adminAdministradora(org)));
        base = "/api/v1/condominiums/" + condo.getId();
    }

    private JsonNode postJson(String url, Object body, int expected) throws Exception {
        String json = mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private Map<String, Object> unit(Object buildingId, String identifier, String fraction) {
        Map<String, Object> body = new HashMap<>();
        body.put("buildingId", buildingId);
        body.put("identifier", identifier);
        body.put("type", "APARTMENT");
        body.put("idealFraction", fraction);
        return body;
    }

    @Test
    void buildingsUnitsAndIdealFractionSummary() throws Exception {
        String blockA = postJson(base + "/buildings", Map.of("name", "Bloco A"), 201).get("id").asText();
        postJson(base + "/buildings", Map.of("name", "bloco a"), 409);

        postJson(base + "/units", unit(blockA, "101", "60.5"), 201);
        postJson(base + "/units", unit(blockA, "101", "1"), 409);
        postJson(base + "/units", unit(null, "101", "1"), 201); // mesmo número fora do bloco é outra unidade

        mvc.perform(get(base + "/units/fraction-summary").header("Authorization", token))
                .andExpect(jsonPath("$.total").value(61.5))
                .andExpect(jsonPath("$.balanced").value(false))
                .andExpect(jsonPath("$.difference").value(38.5));

        postJson(base + "/units", unit(null, "Loja 1", "38.5"), 201);
        mvc.perform(get(base + "/units/fraction-summary").header("Authorization", token))
                .andExpect(jsonPath("$.balanced").value(true));

        postJson(base + "/units", unit(null, "X", "100.5"), 400);
        postJson(base + "/buildings/" + blockA + "/deactivate", null, 422);
    }

    @Test
    void csvImportIsAllOrNothingAndReusesPeopleByDocument() throws Exception {
        String bad = """
                bloco;unidade;tipo;fracao;area;proprietario;documento;email;telefone
                A;101;apartamento;50,0;72,5;Ana Souza;529.982.247-25;ana@ex.com;(91) 98765-4321
                A;102;nave;10;;;;;
                A;101;apartamento;1;;;;;
                """;
        mvc.perform(multipart(base + "/units/import").file(csv(bad)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsCreated").value(0))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].line").value(3));
        assertThat(jdbc.queryForObject("select count(*) from unit where condominium_id = ?", Integer.class, condo.getId())).isZero();

        String good = """
                Bloco;Unidade;Tipo;Fração;Área;Proprietário;Documento;E-mail;Telefone
                A;101;apartamento;50,0;72,5;Ana Souza;529.982.247-25;ana@ex.com;(91) 98765-4321
                A;102;apto;25;;Ana Souza;52998224725;;
                ;Loja 1;loja;25;;Comércio XYZ Ltda;11.222.333/0001-81;;
                """;
        mvc.perform(multipart(base + "/units/import").file(csv(good)).param("dryRun", "true").header("Authorization", token))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.unitsCreated").value(3));
        assertThat(jdbc.queryForObject("select count(*) from unit where condominium_id = ?", Integer.class, condo.getId())).isZero();

        mvc.perform(multipart(base + "/units/import").file(csv(good)).header("Authorization", token))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.unitsCreated").value(3))
                .andExpect(jsonPath("$.buildingsCreated").value(1))
                .andExpect(jsonPath("$.peopleCreated").value(2))
                .andExpect(jsonPath("$.peopleReused").value(1));

        mvc.perform(get(base + "/units").header("Authorization", token).param("search", "101"))
                .andExpect(jsonPath("$.conteudo[0].label").value("A · 101"))
                .andExpect(jsonPath("$.conteudo[0].ownerName").value("Ana Souza"))
                .andExpect(jsonPath("$.conteudo[0].financialResponsibleName").value("Ana Souza"));
        mvc.perform(get(base + "/units/fraction-summary").header("Authorization", token))
                .andExpect(jsonPath("$.balanced").value(true));
    }

    @Test
    void peopleOccupanciesAndFinancialResponsibility() throws Exception {
        String unitId = postJson(base + "/units", unit(null, "201", "10"), 201).get("id").asText();
        String owner = postJson("/api/v1/people", Map.of("name", "Bruno Lima", "kind", "INDIVIDUAL",
                "document", "111.444.777-35", "email", "bruno@ex.com"), 201).get("id").asText();
        postJson("/api/v1/people", Map.of("name", "Outro", "kind", "INDIVIDUAL", "document", "11144477735"), 409);
        postJson("/api/v1/people", Map.of("name", "CPF ruim", "kind", "INDIVIDUAL", "document", "111.444.777-00"), 400);
        postJson("/api/v1/people", Map.of("name", "Empresa", "kind", "COMPANY", "document", "111.444.777-35"), 422);
        String tenant = postJson("/api/v1/people", Map.of("name", "Carla Inquilina", "kind", "INDIVIDUAL"), 201)
                .get("id").asText();

        postJson(base + "/occupancies", Map.of("unitId", unitId, "personId", owner, "role", "OWNER",
                "financialResponsible", true, "startDate", "2024-01-01"), 201);
        postJson(base + "/occupancies", Map.of("unitId", unitId, "personId", tenant, "role", "TENANT",
                "financialResponsible", true, "startDate", "2026-01-01"), 409);
        String tenancy = postJson(base + "/occupancies", Map.of("unitId", unitId, "personId", tenant, "role", "TENANT",
                "financialResponsible", false, "startDate", "2026-01-01"), 201).get("id").asText();

        postJson(base + "/occupancies/" + tenancy + "/financial-responsible", null, 200);
        mvc.perform(get(base + "/units/" + unitId).header("Authorization", token))
                .andExpect(jsonPath("$.financialResponsibleName").value("Carla Inquilina"));

        postJson(base + "/occupancies/" + tenancy + "/end", Map.of("endDate", "2025-12-01"), 422);
        postJson(base + "/occupancies/" + tenancy + "/end", Map.of("endDate", "2026-06-30", "reason", "Fim do contrato"), 200);

        mvc.perform(get(base + "/units/" + unitId + "/occupancies").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].endReason").value("Fim do contrato"));
        mvc.perform(get("/api/v1/people").header("Authorization", token).param("search", "529.982"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/people").header("Authorization", token).param("search", "111444"))
                .andExpect(jsonPath("$.conteudo[0].name").value("Bruno Lima"));
    }

    @Test
    void portalAccessCreatesLinkedUserWithOwnerRole() throws Exception {
        String unitId = postJson(base + "/units", unit(null, "301", "10"), 201).get("id").asText();
        String email = TestData.emailUnico();
        String person = postJson("/api/v1/people", Map.of("name", "Dora Dona", "kind", "INDIVIDUAL", "email", email), 201)
                .get("id").asText();
        postJson("/api/v1/people/" + person + "/portal-access", null, 422); // sem vínculo
        postJson(base + "/occupancies", Map.of("unitId", unitId, "personId", person, "role", "OWNER",
                "financialResponsible", true, "startDate", "2025-01-01"), 201);

        postJson("/api/v1/people/" + person + "/portal-access", null, 200);
        assertThat(mail.ultimoTokenPara(email)).isPresent();
        assertThat(jdbc.queryForObject("""
                select r.code from user_role ur join role r on r.id = ur.role_id join app_user u on u.id = ur.user_id
                where u.person_id = ?::uuid""", String.class, person)).isEqualTo("OWNER");
    }

    @Test
    void scopedUsersSeeOnlyTheirCondominiumAndOtherOrganizationsAreInvisible() throws Exception {
        String unitId = postJson(base + "/units", unit(null, "401", "10"), 201).get("id").asText();
        String person = postJson("/api/v1/people", Map.of("name", "Elisa Moradora", "kind", "INDIVIDUAL"), 201).get("id").asText();
        postJson(base + "/occupancies", Map.of("unitId", unitId, "personId", person, "role", "RESIDENT",
                "financialResponsible", false, "startDate", "2025-01-01"), 201);
        postJson("/api/v1/people", Map.of("name", "Sem Vínculo", "kind", "INDIVIDUAL"), 201);

        Condominium other = data.condominium(org, "Outro");
        Usuario syndic = data.usuario(org);
        data.conceder(syndic, "SYNDIC", other.getId());
        String syndicToken = TestData.bearer(data.login(syndic));
        mvc.perform(get("/api/v1/people").header("Authorization", syndicToken))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get(base + "/units").header("Authorization", syndicToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/people/" + person).header("Authorization", syndicToken)).andExpect(status().isNotFound());

        String foreignToken = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/units").header("Authorization", foreignToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/people/" + person).header("Authorization", foreignToken)).andExpect(status().isNotFound());
    }

    @Test
    void accessibleCondominiumsListsPermissions() throws Exception {
        mvc.perform(get("/api/v1/condominiums/accessible").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Residencial Jatobá"))
                .andExpect(jsonPath("$[0].permissions").isArray());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "unidades.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
