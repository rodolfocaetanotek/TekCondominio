package br.com.tekcondo.identity;

import java.util.Map;

import br.com.tekcondo.identity.controller.AuthController;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.RecordingMailGateway;
import br.com.tekcondo.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordIT extends IntegrationTest {

    @Autowired
    RecordingMailGateway mail;
    @Autowired
    JdbcTemplate jdbc;

    private void forgot(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", email))))
                .andExpect(status().isAccepted());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", email, "password", password)))).andReturn();
    }

    @Test
    void unknownEmailGetsSameResponseAndNoMail() throws Exception {
        String email = TestData.uniqueEmail();
        forgot(email);
        assertThat(mail.to(email)).isEmpty();
    }

    @Test
    void resetFlowChangesPasswordUnlocksAccountAndEndsSessions() throws Exception {
        User user = data.orgAdmin(data.organization());
        Cookie session = login(user.getEmail(), TestData.PASSWORD).getResponse().getCookie(AuthController.REFRESH_COOKIE);
        // Conta bloqueada por tentativas (perfil de teste: 3)
        for (int i = 0; i < 3; i++) {
            login(user.getEmail(), "errada12345");
        }
        assertThat(login(user.getEmail(), TestData.PASSWORD).getResponse().getStatus()).isEqualTo(423);

        forgot(user.getEmail());
        String token = mail.lastTokenFor(user.getEmail()).orElseThrow();
        assertThat(mail.to(user.getEmail()).getLast().body()).contains("/definir-senha#token=");

        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token, "newPassword", "fraca"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token, "newPassword", "NovaSenha2026"))))
                .andExpect(status().isNoContent());

        assertThat(login(user.getEmail(), "NovaSenha2026").getResponse().getStatus()).isEqualTo(200);
        assertThat(login(user.getEmail(), TestData.PASSWORD).getResponse().getStatus()).isEqualTo(401);
        // Sessão aberta antes da redefinição foi encerrada.
        mvc.perform(post("/api/v1/auth/refresh").cookie(session).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());

        // Link é de uso único.
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token, "newPassword", "OutraSenha2026"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_LINK"));
    }

    @Test
    void expiredOrForgedLinksAreRejected() throws Exception {
        User user = data.orgAdmin(data.organization());
        forgot(user.getEmail());
        String token = mail.lastTokenFor(user.getEmail()).orElseThrow();
        jdbc.update("update password_token set expires_at = now() - interval '1 minute' where user_id = ?", user.getId());

        mvc.perform(post("/api/v1/auth/password/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token))))
                .andExpect(status().isGone());
        mvc.perform(post("/api/v1/auth/password/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", "inventado"))))
                .andExpect(status().isGone());
    }

    @Test
    void resetRequestsAreRateLimited() throws Exception {
        User user = data.orgAdmin(data.organization());
        for (int i = 0; i < 5; i++) {
            forgot(user.getEmail());
        }
        assertThat(mail.to(user.getEmail())).hasSize(3);
    }

    @Test
    void inactiveUserDoesNotReceiveResetLink() throws Exception {
        User user = data.orgAdmin(data.organization());
        jdbc.update("update app_user set active = false where id = ?", user.getId());
        forgot(user.getEmail());
        assertThat(mail.to(user.getEmail())).isEmpty();
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndKeepsCurrentSession() throws Exception {
        User user = data.orgAdmin(data.organization());
        MvcResult other = login(user.getEmail(), TestData.PASSWORD);
        Cookie otherSession = other.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        MvcResult current = login(user.getEmail(), TestData.PASSWORD);
        Cookie currentSession = current.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        String token = "Bearer " + new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(current.getResponse().getContentAsString()).get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/password/change").header("Authorization", token).cookie(currentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("currentPassword", "errada12345", "newPassword", "NovaSenha2026"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));

        mvc.perform(post("/api/v1/auth/password/change").header("Authorization", token).cookie(currentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("currentPassword", TestData.PASSWORD, "newPassword", "NovaSenha2026"))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(currentSession).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").cookie(otherSession).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/password/change").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("currentPassword", "x", "newPassword", "NovaSenha2026"))))
                .andExpect(status().isUnauthorized());
    }
}
