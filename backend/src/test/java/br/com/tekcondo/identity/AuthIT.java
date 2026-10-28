package br.com.tekcondo.identity;

import java.util.Map;

import br.com.tekcondo.identity.controller.AuthController;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.organization.entity.Organization;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", email, "password", password)))).andReturn();
    }

    private Cookie refreshCookie(MvcResult result) {
        return result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        User user = data.orgAdmin(data.organization());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", user.getEmail().toUpperCase(), "password", TestData.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(content().string(not(containsString("refresh"))))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));

        Integer audits = jdbc.queryForObject(
                "select count(*) from audit_log where user_id = ? and action = 'LOGIN_SUCCESS'", Integer.class, user.getId());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameGenericError() throws Exception {
        User user = data.orgAdmin(data.organization());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", user.getEmail(), "password", "errada12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", "naoexiste@teste.tekcondo", "password", "errada12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountIsLockedAfterRepeatedFailuresEvenWithCorrectPassword() throws Exception {
        User user = data.orgAdmin(data.organization()); // perfil de teste: 3 tentativas

        for (int i = 0; i < 2; i++) {
            assertThat(login(user.getEmail(), "errada12345").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login(user.getEmail(), "errada12345").getResponse().getStatus()).isEqualTo(423);
        MvcResult blocked = login(user.getEmail(), TestData.PASSWORD);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(423);
        assertThat(blocked.getResponse().getContentAsString()).contains("ACCOUNT_LOCKED");

        Integer lockAudits = jdbc.queryForObject(
                "select count(*) from audit_log where user_id = ? and action = 'ACCOUNT_LOCKED'", Integer.class, user.getId());
        assertThat(lockAudits).isEqualTo(1);
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesAllSessions() throws Exception {
        User user = data.orgAdmin(data.organization());
        Cookie first = refreshCookie(login(user.getEmail(), TestData.PASSWORD));

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first)
                        .header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Reutilizar o token antigo (possível roubo) invalida inclusive o novo.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(second).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void refreshRequiresAntiCsrfHeader() throws Exception {
        User user = data.orgAdmin(data.organization());
        Cookie cookie = refreshCookie(login(user.getEmail(), TestData.PASSWORD));

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        User user = data.orgAdmin(data.organization());
        Cookie cookie = refreshCookie(login(user.getEmail(), TestData.PASSWORD));

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsProfileWithPermissions() throws Exception {
        Organization org = data.organization();
        User user = data.orgAdmin(org);
        String token = data.login(user);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.organizationName").value(org.getName()))
                .andExpect(jsonPath("$.organizationPermissions", hasItem("CONDOMINIUM_CREATE")));
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/api/v1/condominiums")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/condominiums").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveOrganizationUsersCannotSignIn() throws Exception {
        Organization org = data.organization();
        User user = data.orgAdmin(org);
        jdbc.update("update organization set active = false where id = ?", org.getId());

        assertThat(login(user.getEmail(), TestData.PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }
}
