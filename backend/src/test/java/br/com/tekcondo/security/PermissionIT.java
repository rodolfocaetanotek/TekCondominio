package br.com.tekcondo.security;

import java.util.Map;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.organization.entity.Organization;
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

    Organization org;
    Condominium mine;
    Condominium other;

    @BeforeEach
    void setUp() {
        org = data.organization();
        mine = data.condominium(org, "Condomínio do Síndico");
        other = data.condominium(org, "Outro Condomínio");
    }

    private String syndicToken() {
        User syndic = data.user(org);
        data.grant(syndic, "SYNDIC", mine.getId());
        return TestData.bearer(data.login(syndic));
    }

    @Test
    void syndicSeesAndEditsOnlyTheirCondominium() throws Exception {
        String token = syndicToken();

        mvc.perform(get("/api/v1/condominiums").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(mine.getId().toString()));

        mvc.perform(get("/api/v1/condominiums/" + other.getId()).header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Map<String, Object> body = Map.of("name", "Nome Atualizado", "type", "MIXED", "version", 0);
        mvc.perform(put("/api/v1/condominiums/" + mine.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/condominiums/" + other.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void syndicCannotCreateOrInactivateCondominiums() throws Exception {
        String token = syndicToken();

        mvc.perform(post("/api/v1/condominiums").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("name", "Novo", "type", "RESIDENTIAL"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/condominiums/" + mine.getId() + "/inactivate").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("reason", "Sem permissão"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        String token = TestData.bearer(data.login(data.user(org)));

        mvc.perform(get("/api/v1/condominiums").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void residentRoleHasNoAdministrativeAccess() throws Exception {
        User resident = data.user(org);
        data.grant(resident, "RESIDENT", mine.getId());
        String token = TestData.bearer(data.login(resident));

        mvc.perform(get("/api/v1/condominiums/" + mine.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationAdminCannotManageOrganizations() throws Exception {
        String token = TestData.bearer(data.login(data.orgAdmin(org)));

        mvc.perform(get("/api/v1/organizations").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void platformAdminHasNoAccessToTenantData() throws Exception {
        String token = TestData.bearer(data.login(data.platformAdmin()));

        mvc.perform(get("/api/v1/condominiums").header("Authorization", token)).andExpect(status().isForbidden());
    }
}
