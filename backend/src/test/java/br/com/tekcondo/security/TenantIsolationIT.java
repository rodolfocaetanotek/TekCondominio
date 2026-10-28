package br.com.tekcondo.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.organization.entity.Organization;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comprova que uma administradora não acessa, altera nem vincula dados de outra. */
class TenantIsolationIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    Organization orgA;
    Organization orgB;
    Condominium condoB;
    String tokenA;

    @BeforeEach
    void setUp() {
        orgA = data.organization();
        orgB = data.organization();
        data.condominium(orgA, "Condomínio da A");
        condoB = data.condominium(orgB, "Condomínio da B");
        tokenA = TestData.bearer(data.login(data.orgAdmin(orgA)));
    }

    @Test
    void listNeverIncludesOtherOrganizationsData() throws Exception {
        mvc.perform(get("/api/v1/condominiums").header("Authorization", tokenA).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Condomínio da A")))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(condoB.getId().toString()))));
    }

    @Test
    void readingAnotherOrganizationsRecordReturns404() throws Exception {
        mvc.perform(get("/api/v1/condominiums/" + condoB.getId()).header("Authorization", tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void writingAnotherOrganizationsRecordReturns404() throws Exception {
        Map<String, Object> body = Map.of("name", "Invadido", "type", "RESIDENTIAL", "version", 0);
        mvc.perform(put("/api/v1/condominiums/" + condoB.getId()).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/condominiums/" + condoB.getId() + "/inactivate").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("reason", "Tentativa indevida"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditTrailIsScopedToOwnOrganization() throws Exception {
        String tokenB = TestData.bearer(data.login(data.orgAdmin(orgB)));
        mvc.perform(get("/api/v1/audit").header("Authorization", tokenA).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].entityId", not(hasItem(condoB.getId().toString()))));
        mvc.perform(get("/api/v1/audit").header("Authorization", tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void databaseRejectsRoleAssignmentToCondominiumOfAnotherOrganization() {
        User userA = data.user(orgA);
        // FK composta (condominium_id, organization_id): o condomínio da B não pertence à organização A.
        assertThatThrownBy(() -> jdbc.update("""
                        insert into user_role (id, user_id, role_id, organization_id, condominium_id, created_at)
                        values (?, ?, '10000000-0000-0000-0000-000000000003', ?, ?, ?)""",
                UUID.randomUUID(), userA.getId(), orgA.getId(), condoB.getId(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAssigningUserToAnotherOrganization() {
        User userA = data.user(orgA);
        assertThatThrownBy(() -> jdbc.update("""
                        insert into user_role (id, user_id, role_id, organization_id, condominium_id, created_at)
                        values (?, ?, '10000000-0000-0000-0000-000000000001', ?, null, ?)""",
                UUID.randomUUID(), userA.getId(), orgB.getId(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
