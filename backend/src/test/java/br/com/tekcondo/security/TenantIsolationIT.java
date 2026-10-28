package br.com.tekcondo.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.administradora.entity.Administradora;
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

    Administradora orgA;
    Administradora orgB;
    Condominio condoB;
    String tokenA;

    @BeforeEach
    void setUp() {
        orgA = data.administradora();
        orgB = data.administradora();
        data.condominio(orgA, "Condomínio da A");
        condoB = data.condominio(orgB, "Condomínio da B");
        tokenA = TestData.bearer(data.login(data.adminAdministradora(orgA)));
    }

    @Test
    void listNeverIncludesOtherOrganizationsData() throws Exception {
        mvc.perform(get("/api/v1/condominios").header("Authorization", tokenA).param("tamanho", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].nome", hasItem("Condomínio da A")))
                .andExpect(jsonPath("$.conteudo[*].id", not(hasItem(condoB.getId().toString()))));
    }

    @Test
    void readingAnotherOrganizationsRecordReturns404() throws Exception {
        mvc.perform(get("/api/v1/condominios/" + condoB.getId()).header("Authorization", tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void writingAnotherOrganizationsRecordReturns404() throws Exception {
        Map<String, Object> body = Map.of("nome", "Invadido", "tipo", "RESIDENTIAL", "versao", 0);
        mvc.perform(put("/api/v1/condominios/" + condoB.getId()).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(body)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/condominios/" + condoB.getId() + "/inativar").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("motivo", "Tentativa indevida"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditTrailIsScopedToOwnOrganization() throws Exception {
        String tokenB = TestData.bearer(data.login(data.adminAdministradora(orgB)));
        mvc.perform(get("/api/v1/auditoria").header("Authorization", tokenA).param("tamanho", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].idEntidade", not(hasItem(condoB.getId().toString()))));
        mvc.perform(get("/api/v1/auditoria").header("Authorization", tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void databaseRejectsRoleAssignmentToCondominiumOfAnotherOrganization() {
        Usuario userA = data.usuario(orgA);
        // FK composta (condominium_id, organization_id): o condomínio da B não pertence à organização A.
        assertThatThrownBy(() -> jdbc.update("""
                        insert into user_role (id, user_id, role_id, organization_id, condominium_id, created_at)
                        values (?, ?, '10000000-0000-0000-0000-000000000003', ?, ?, ?)""",
                UUID.randomUUID(), userA.getId(), orgA.getId(), condoB.getId(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAssigningUserToAnotherOrganization() {
        Usuario userA = data.usuario(orgA);
        assertThatThrownBy(() -> jdbc.update("""
                        insert into user_role (id, user_id, role_id, organization_id, condominium_id, created_at)
                        values (?, ?, '10000000-0000-0000-0000-000000000001', ?, null, ?)""",
                UUID.randomUUID(), userA.getId(), orgB.getId(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
