package br.com.tekcondo.organization;

import java.util.Map;

import br.com.tekcondo.support.Cnpjs;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationIT extends IntegrationTest {

    String platformToken;

    @BeforeEach
    void setUp() {
        platformToken = TestData.bearer(data.login(data.platformAdmin()));
    }

    private Map<String, Object> body(String cnpj, String adminEmail, String password) {
        return Map.of("name", "Administradora Horizonte", "cnpj", cnpj,
                "admin", Map.of("name", "Maria Gestora", "email", adminEmail, "password", password));
    }

    @Test
    void platformAdminCreatesOrganizationWhoseAdminCanSignInAndCreateCondominiums() throws Exception {
        String adminEmail = TestData.uniqueEmail();
        mvc.perform(post("/api/v1/organizations").header("Authorization", platformToken)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body(Cnpjs.random(), adminEmail, "SenhaInicial2026"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", adminEmail, "password", "SenhaInicial2026"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + new com.fasterxml.jackson.databind.ObjectMapper().readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", token))
                .andExpect(jsonPath("$.organizationName").value("Administradora Horizonte"))
                .andExpect(jsonPath("$.organizationPermissions", hasItem("CONDOMINIUM_CREATE")));
        mvc.perform(post("/api/v1/condominiums").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("name", "Primeiro Condomínio", "type", "COMMERCIAL"))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateCnpjAndWeakPassword() throws Exception {
        String cnpj = Cnpjs.random();
        mvc.perform(post("/api/v1/organizations").header("Authorization", platformToken)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body(cnpj, TestData.uniqueEmail(), "SenhaInicial2026"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/organizations").header("Authorization", platformToken)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body(cnpj, TestData.uniqueEmail(), "SenhaInicial2026"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_CNPJ_TAKEN"));

        mvc.perform(post("/api/v1/organizations").header("Authorization", platformToken)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body(Cnpjs.random(), TestData.uniqueEmail(), "fraca"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("admin.password"));
    }
}
