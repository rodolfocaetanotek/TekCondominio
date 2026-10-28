package br.com.tekcondo.condominium;

import java.util.HashMap;
import java.util.Map;

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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CondominiumIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;

    String token;

    @BeforeEach
    void setUp() {
        Administradora org = data.administradora();
        Usuario admin = data.adminAdministradora(org);
        token = TestData.bearer(data.login(admin));
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Residencial Jardim das Flores");
        body.put("legalName", "Condomínio Edifício Jardim das Flores");
        body.put("cnpj", "12.ABC.345/01DE-35");
        body.put("type", "RESIDENTIAL");
        body.put("email", "sindico@jardim.com.br");
        body.put("phone", "(11) 98765-4321");
        body.put("address", Map.of("zipCode", "01310-100", "street", "Av. Paulista", "number", "1000",
                "district", "Bela Vista", "city", "São Paulo", "state", "sp"));
        return body;
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        String json = mvc.perform(post("/api/v1/condominiums").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void createsAndReadsCondominiumWithNormalizedData() throws Exception {
        String body = mvc.perform(post("/api/v1/condominiums").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(validBody())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.cnpj").value("12ABC34501DE35"))
                .andExpect(jsonPath("$.phone").value("11987654321"))
                .andExpect(jsonPath("$.address.zipCode").value("01310100"))
                .andExpect(jsonPath("$.address.state").value("SP"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mvc.perform(get("/api/v1/condominiums/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Residencial Jardim das Flores"));

        Integer audits = jdbc.queryForObject(
                "select count(*) from audit_log where entity_id = ? and action = 'CONDOMINIUM_CREATED'", Integer.class, id);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void validationErrorsAreReturnedPerFieldInPortuguese() throws Exception {
        Map<String, Object> body = validBody();
        body.put("name", "");
        body.put("cnpj", "11.111.111/1111-11");
        body.put("type", null);
        body.put("address", Map.of("zipCode", "123", "state", "XX"));

        mvc.perform(post("/api/v1/condominiums").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("name")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("cnpj")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("type")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("address.zipCode")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("address.state")))
                .andExpect(jsonPath("$.erros[*].mensagem", hasItem("CNPJ inválido.")));
    }

    @Test
    void duplicateCnpjInSameOrganizationIsRejected() throws Exception {
        create(validBody());

        mvc.perform(post("/api/v1/condominiums").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(validBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONDOMINIUM_CNPJ_TAKEN"));
    }

    @Test
    void updateUsesOptimisticLockingAndAuditsChanges() throws Exception {
        JsonNode created = create(validBody());
        String id = created.get("id").asText();

        Map<String, Object> update = validBody();
        update.put("name", "Residencial Jardim Renovado");
        update.put("version", 0);
        mvc.perform(put("/api/v1/condominiums/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Residencial Jardim Renovado"))
                .andExpect(jsonPath("$.version").value(1));

        // Segunda edição com a versão antiga: outro usuário já alterou o registro.
        mvc.perform(put("/api/v1/condominiums/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STALE_VERSION"));

        String details = jdbc.queryForObject(
                "select details::text from audit_log where entity_id = ? and action = 'CONDOMINIUM_UPDATED'", String.class, id);
        assertThat(details).contains("Residencial Jardim Renovado").contains("Residencial Jardim das Flores");
    }

    @Test
    void inactivateAndActivateKeepHistory() throws Exception {
        String id = create(validBody()).get("id").asText();

        mvc.perform(post("/api/v1/condominiums/" + id + "/inactivate").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("reason", "x"))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/condominiums/" + id + "/inactivate").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("reason", "Fim do contrato de administração"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.inactivationReason").value("Fim do contrato de administração"));

        Map<String, Object> update = validBody();
        update.put("version", 1);
        mvc.perform(put("/api/v1/condominiums/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(update)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONDOMINIUM_INACTIVE"));

        mvc.perform(post("/api/v1/condominiums/" + id + "/activate").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listSupportsSearchStatusFilterPaginationAndSort() throws Exception {
        for (String name : new String[]{"Edifício Bravo", "Edifício Alfa", "Condomínio Charlie"}) {
            Map<String, Object> body = validBody();
            body.put("name", name);
            body.remove("cnpj");
            body.remove("legalName");
            create(body);
        }

        mvc.perform(get("/api/v1/condominiums").header("Authorization", token)
                        .param("search", "edifício").param("ordem", "name,asc").param("tamanho", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.conteudo[0].name").value("Edifício Alfa"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        mvc.perform(get("/api/v1/condominiums").header("Authorization", token).param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));

        mvc.perform(get("/api/v1/condominiums").header("Authorization", token).param("ordem", "password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("INVALID_SORT"));
    }

    @Test
    void searchByMaskedCnpjFindsCondominium() throws Exception {
        create(validBody());

        mvc.perform(get("/api/v1/condominiums").header("Authorization", token).param("search", "12.abc.345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }
}
