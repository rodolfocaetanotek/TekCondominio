package br.com.tekcondo.acesso;

import java.util.Map;

import br.com.tekcondo.acesso.controller.AuthController;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.administradora.entity.Administradora;
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

    private MvcResult login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", email, "senha", senha)))).andReturn();
    }

    private Cookie cookieRefresh(MvcResult resultado) {
        return resultado.getResponse().getCookie(AuthController.REFRESH_COOKIE);
    }

    @Test
    void loginRetornaAccessTokenECookieRefreshHttpOnly() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", usuario.getEmail().toUpperCase(), "senha", TestData.SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(content().string(not(containsString("refresh"))))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));

        Integer audits = jdbc.queryForObject(
                "select count(*) from audit_log where user_id = ? and action = 'LOGIN_SUCCESS'", Integer.class, usuario.getId());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void senhaErradaEEmailDesconhecidoRetornamMesmoErroGenerico() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", usuario.getEmail(), "senha", "errada12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", "naoexiste@teste.tekcondo", "senha", "errada12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("INVALID_CREDENTIALS"));
    }

    @Test
    void contaBloqueadaAposFalhasRepetidasMesmoComSenhaCorreta() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora()); // perfil de teste: 3 tentativas

        for (int i = 0; i < 2; i++) {
            assertThat(login(usuario.getEmail(), "errada12345").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login(usuario.getEmail(), "errada12345").getResponse().getStatus()).isEqualTo(423);
        MvcResult blocked = login(usuario.getEmail(), TestData.SENHA);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(423);
        assertThat(blocked.getResponse().getContentAsString()).contains("ACCOUNT_LOCKED");

        Integer lockAudits = jdbc.queryForObject(
                "select count(*) from audit_log where user_id = ? and action = 'ACCOUNT_LOCKED'", Integer.class, usuario.getId());
        assertThat(lockAudits).isEqualTo(1);
    }

    @Test
    void refreshRotacionaTokenEReusoRevogaTodasAsSessoes() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        Cookie first = cookieRefresh(login(usuario.getEmail(), TestData.SENHA));

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first)
                        .header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = cookieRefresh(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Reutilizar o token antigo (possível roubo) invalida inclusive o novo.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(second).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSION_EXPIRED"));
    }

    @Test
    void refreshExigeCabecalhoAntiCsrf() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        Cookie cookie = cookieRefresh(login(usuario.getEmail(), TestData.SENHA));

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevogaRefreshToken() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        Cookie cookie = cookieRefresh(login(usuario.getEmail(), TestData.SENHA));

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilRetornaDadosComPermissoes() throws Exception {
        Administradora org = data.administradora();
        Usuario usuario = data.adminAdministradora(org);
        String token = data.login(usuario);

        mvc.perform(get("/api/v1/auth/perfil").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.nomeAdministradora").value(org.getNome()))
                .andExpect(jsonPath("$.permissoesAdministradora", hasItem("CONDOMINIUM_CREATE")));
    }

    @Test
    void endpointsProtegidosExigemTokenValido() throws Exception {
        mvc.perform(get("/api/v1/condominios")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/condominios").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuariosDeAdministradoraInativaNaoEntram() throws Exception {
        Administradora org = data.administradora();
        Usuario usuario = data.adminAdministradora(org);
        jdbc.update("update organization set active = false where id = ?", org.getId());

        assertThat(login(usuario.getEmail(), TestData.SENHA).getResponse().getStatus()).isEqualTo(401);
    }
}
