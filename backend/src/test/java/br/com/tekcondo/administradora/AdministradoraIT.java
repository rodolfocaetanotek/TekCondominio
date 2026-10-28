package br.com.tekcondo.administradora;

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

class AdministradoraIT extends IntegrationTest {

    String tokenPlataforma;

    @BeforeEach
    void setUp() {
        tokenPlataforma = TestData.bearer(data.login(data.administradorPlataforma()));
    }

    private Map<String, Object> corpo(String cnpj, String emailAdministrador, String senha) {
        return Map.of("nome", "Administradora Horizonte", "cnpj", cnpj,
                "administrador", Map.of("nome", "Maria Gestora", "email", emailAdministrador, "senha", senha));
    }

    @Test
    void administradorPlataformaCadastraAdministradoraCujoAdministradorEntraECriaCondominios() throws Exception {
        String emailAdministrador = TestData.emailUnico();
        mvc.perform(post("/api/v1/administradoras").header("Authorization", tokenPlataforma)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpo(Cnpjs.random(), emailAdministrador, "SenhaInicial2026"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true));

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", emailAdministrador, "senha", "SenhaInicial2026"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + new com.fasterxml.jackson.databind.ObjectMapper().readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/perfil").header("Authorization", token))
                .andExpect(jsonPath("$.nomeAdministradora").value("Administradora Horizonte"))
                .andExpect(jsonPath("$.permissoesAdministradora", hasItem("CONDOMINIUM_CREATE")));
        mvc.perform(post("/api/v1/condominiums").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("name", "Primeiro Condomínio", "type", "COMMERCIAL"))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejeitaCnpjDuplicadoESenhaFraca() throws Exception {
        String cnpj = Cnpjs.random();
        mvc.perform(post("/api/v1/administradoras").header("Authorization", tokenPlataforma)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpo(cnpj, TestData.emailUnico(), "SenhaInicial2026"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/administradoras").header("Authorization", tokenPlataforma)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpo(cnpj, TestData.emailUnico(), "SenhaInicial2026"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORGANIZATION_CNPJ_TAKEN"));

        mvc.perform(post("/api/v1/administradoras").header("Authorization", tokenPlataforma)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpo(Cnpjs.random(), TestData.emailUnico(), "fraca"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("administrador.senha"));
    }
}
