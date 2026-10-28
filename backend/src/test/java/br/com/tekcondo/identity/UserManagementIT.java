package br.com.tekcondo.identity;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    RecordingMailGateway mail;
    @Autowired
    JdbcTemplate jdbc;

    Organization org;
    User admin;
    Condominium condo;
    String token;

    @BeforeEach
    void setUp() {
        org = data.organization();
        admin = data.orgAdmin(org);
        condo = data.condominium(org, "Residencial Sabiá");
        token = TestData.bearer(data.login(admin));
    }

    private Map<String, Object> createBody(String email, String roleCode, Object condominiumId) {
        Map<String, Object> assignment = new HashMap<>();
        assignment.put("roleCode", roleCode);
        assignment.put("condominiumId", condominiumId);
        return Map.of("name", "Carlos Síndico", "email", email, "assignment", assignment);
    }

    private JsonNode createUser(String email, String roleCode, Object condominiumId) throws Exception {
        String json = mvc.perform(post("/api/v1/users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(createBody(email, roleCode, condominiumId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void createdUserReceivesInviteDefinesPasswordAndSignsInWithScopedAccess() throws Exception {
        String email = TestData.uniqueEmail();
        JsonNode created = createUser(email, "SYNDIC", condo.getId());
        assertThat(created.get("pendingInvite").asBoolean()).isTrue();
        assertThat(created.get("assignments").get(0).get("condominiumName").asText()).isEqualTo("Residencial Sabiá");

        // Sem senha definida, não consegue entrar.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", email, "password", TestData.PASSWORD))))
                .andExpect(status().isUnauthorized());

        String invite = mail.lastTokenFor(email).orElseThrow();
        assertThat(mail.to(email).getLast().subject()).contains("Convite");
        mvc.perform(post("/api/v1/auth/password/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", invite))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("INVITE"))
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", invite, "newPassword", "MinhaSenha2026"))))
                .andExpect(status().isNoContent());

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", email, "password", "MinhaSenha2026"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String syndicToken = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/v1/condominiums").header("Authorization", syndicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        // Síndico não gerencia usuários.
        mvc.perform(get("/api/v1/users").header("Authorization", syndicToken)).andExpect(status().isForbidden());
    }

    @Test
    void listsAndSearchesUsersOfOwnOrganizationOnly() throws Exception {
        createUser(TestData.uniqueEmail(), "ORG_STAFF", null);
        User outsider = data.orgAdmin(data.organization());

        mvc.perform(get("/api/v1/users").header("Authorization", token).param("search", "carlos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roles", hasItem("Funcionário da administradora")));

        mvc.perform(get("/api/v1/users/" + outsider.getId()).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void roleScopeIsValidated() throws Exception {
        mvc.perform(post("/api/v1/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(createBody(TestData.uniqueEmail(), "SYNDIC", null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_SCOPE_MISMATCH"));
        mvc.perform(post("/api/v1/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(createBody(TestData.uniqueEmail(), "ORG_ADMIN", condo.getId()))))
                .andExpect(status().isUnprocessableEntity());

        Condominium foreign = data.condominium(data.organization(), "Condomínio de outra administradora");
        mvc.perform(post("/api/v1/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(createBody(TestData.uniqueEmail(), "SYNDIC", foreign.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailAndDuplicateAssignmentAreRejected() throws Exception {
        String email = TestData.uniqueEmail();
        String id = createUser(email, "COUNCIL", condo.getId()).get("id").asText();

        mvc.perform(post("/api/v1/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(createBody(email.toUpperCase(), "COUNCIL", condo.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_TAKEN"));

        mvc.perform(post("/api/v1/users/" + id + "/assignments").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("roleCode", "COUNCIL", "condominiumId", condo.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ALREADY_ASSIGNED"));
    }

    @Test
    void assignAndRevokeRolesAreAudited() throws Exception {
        String id = createUser(TestData.uniqueEmail(), "ORG_STAFF", null).get("id").asText();

        String detail = mvc.perform(post("/api/v1/users/" + id + "/assignments").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("roleCode", "SYNDIC", "condominiumId", condo.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = null;
        for (JsonNode a : objectMapper.readTree(detail).get("assignments")) {
            if (a.get("roleCode").asText().equals("SYNDIC")) {
                assignmentId = a.get("id").asText();
            }
        }

        mvc.perform(delete("/api/v1/users/" + id + "/assignments/" + assignmentId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(1)));

        Integer audits = jdbc.queryForObject("""
                select count(*) from audit_log where entity_id = ? and action in ('ROLE_ASSIGNED', 'ROLE_REVOKED')""",
                Integer.class, id);
        assertThat(audits).isEqualTo(3);
    }

    @Test
    void organizationAlwaysKeepsAnActiveAdministrator() throws Exception {
        // Único administrador não pode remover o próprio papel de administrador.
        String detail = mvc.perform(get("/api/v1/users/" + admin.getId()).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adminAssignment = objectMapper.readTree(detail).get("assignments").get(0).get("id").asText();
        mvc.perform(delete("/api/v1/users/" + admin.getId() + "/assignments/" + adminAssignment).header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        mvc.perform(post("/api/v1/users/" + admin.getId() + "/deactivate").header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        User staff = data.user(org);
        data.grant(staff, "ORG_STAFF", null);
        String staffToken = TestData.bearer(data.login(staff));
        mvc.perform(get("/api/v1/condominiums").header("Authorization", staffToken)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/users/" + staff.getId() + "/deactivate").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Token de acesso ainda não expirou, mas as permissões são lidas do banco: acesso negado na hora.
        mvc.perform(get("/api/v1/condominiums").header("Authorization", staffToken)).andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/users/" + staff.getId() + "/activate").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateNameUsesOptimisticLock() throws Exception {
        String id = createUser(TestData.uniqueEmail(), "ORG_STAFF", null).get("id").asText();

        mvc.perform(put("/api/v1/users/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("name", "Carlos Souza", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carlos Souza"));
        mvc.perform(put("/api/v1/users/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("name", "Outro Nome", "version", 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void rolesCatalogExposesScope() throws Exception {
        mvc.perform(get("/api/v1/roles").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'ORG_ADMIN')].scope").value(hasItem("ORGANIZATION")))
                .andExpect(jsonPath("$[?(@.code == 'SYNDIC')].scope").value(hasItem("CONDOMINIUM")));
    }

    @Test
    void resendInviteOnlyForPendingUsers() throws Exception {
        String email = TestData.uniqueEmail();
        String id = createUser(email, "ORG_STAFF", null).get("id").asText();
        String first = mail.lastTokenFor(email).orElseThrow();

        mvc.perform(post("/api/v1/users/" + id + "/invite").header("Authorization", token)).andExpect(status().isAccepted());
        String second = mail.lastTokenFor(email).orElseThrow();
        assertThat(second).isNotEqualTo(first);

        // O convite anterior foi invalidado pelo novo.
        mvc.perform(post("/api/v1/auth/password/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", first))))
                .andExpect(status().isGone());

        mvc.perform(post("/api/v1/users/" + admin.getId() + "/invite").header("Authorization", token))
                .andExpect(status().isUnprocessableEntity());
    }
}
