package br.com.tekcondo.security;

import java.util.Map;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permissões por papel e por condomínio, sempre verificadas no backend. */
class PermissionIT extends IntegrationTest {

    Administradora org;
    Condominio mine;
    Condominio other;

    @BeforeEach
    void setUp() {
        org = data.administradora();
        mine = data.condominio(org, "Condomínio do Síndico");
        other = data.condominio(org, "Outro Condomínio");
    }

    private String syndicToken() {
        Usuario syndic = data.usuario(org);
        data.conceder(syndic, "SYNDIC", mine.getId());
        return TestData.bearer(data.login(syndic));
    }

    @Test
    void syndicSeesAndEditsOnlyTheirCondominium() throws Exception {
        String token = syndicToken();

        mvc.perform(get("/api/v1/condominios").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.conteudo[0].id").value(mine.getId().toString()));

        mvc.perform(get("/api/v1/condominios/" + other.getId()).header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FORBIDDEN"));

        Map<String, Object> body = Map.of("nome", "Nome Atualizado", "tipo", "MIXED", "versao", 0);
        mvc.perform(put("/api/v1/condominios/" + mine.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/condominios/" + other.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void syndicCannotCreateOrInactivateCondominiums() throws Exception {
        String token = syndicToken();

        mvc.perform(post("/api/v1/condominios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("nome", "Novo", "tipo", "RESIDENTIAL"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/condominios/" + mine.getId() + "/inativar").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("motivo", "Sem permissão"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        String token = TestData.bearer(data.login(data.usuario(org)));

        mvc.perform(get("/api/v1/condominios").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auditoria").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void residentRoleHasNoAdministrativeAccess() throws Exception {
        Usuario resident = data.usuario(org);
        data.conceder(resident, "RESIDENT", mine.getId());
        String token = TestData.bearer(data.login(resident));

        mvc.perform(get("/api/v1/condominios/" + mine.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationAdminCannotManageOrganizations() throws Exception {
        String token = TestData.bearer(data.login(data.adminAdministradora(org)));

        mvc.perform(get("/api/v1/administradoras").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void platformAdminHasNoAccessToTenantData() throws Exception {
        String token = TestData.bearer(data.login(data.administradorPlataforma()));

        mvc.perform(get("/api/v1/condominios").header("Authorization", token)).andExpect(status().isForbidden());
    }
}
