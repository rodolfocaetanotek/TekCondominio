package br.com.tekcondo.acesso;

import java.util.Map;

import br.com.tekcondo.acesso.controller.AuthController;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.GravacaoEmailGateway;
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

class SenhaIT extends IntegrationTest {

    @Autowired
    GravacaoEmailGateway emails;
    @Autowired
    JdbcTemplate jdbc;

    private void esqueci(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/senha/esqueci").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", email))))
                .andExpect(status().isAccepted());
    }

    private MvcResult login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", email, "senha", senha)))).andReturn();
    }

    @Test
    void emailDesconhecidoRecebeMesmaRespostaESemEmail() throws Exception {
        String email = TestData.emailUnico();
        esqueci(email);
        assertThat(emails.para(email)).isEmpty();
    }

    @Test
    void redefinicaoTrocaSenhaDesbloqueiaContaEEncerraSessoes() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        Cookie sessao = login(usuario.getEmail(), TestData.SENHA).getResponse().getCookie(AuthController.REFRESH_COOKIE);
        // Conta bloqueada por tentativas (perfil de teste: 3)
        for (int i = 0; i < 3; i++) {
            login(usuario.getEmail(), "errada12345");
        }
        assertThat(login(usuario.getEmail(), TestData.SENHA).getResponse().getStatus()).isEqualTo(423);

        esqueci(usuario.getEmail());
        String token = emails.ultimoTokenPara(usuario.getEmail()).orElseThrow();
        assertThat(emails.para(usuario.getEmail()).getLast().texto()).contains("/definir-senha#token=");

        mvc.perform(post("/api/v1/auth/senha/redefinir").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token, "novaSenha", "fraca"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/senha/redefinir").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token, "novaSenha", "NovaSenha2026"))))
                .andExpect(status().isNoContent());

        assertThat(login(usuario.getEmail(), "NovaSenha2026").getResponse().getStatus()).isEqualTo(200);
        assertThat(login(usuario.getEmail(), TestData.SENHA).getResponse().getStatus()).isEqualTo(401);
        // Sessão aberta antes da redefinição foi encerrada.
        mvc.perform(post("/api/v1/auth/refresh").cookie(sessao).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());

        // Link é de uso único.
        mvc.perform(post("/api/v1/auth/senha/redefinir").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token, "novaSenha", "OutraSenha2026"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("INVALID_LINK"));
    }

    @Test
    void linksExpiradosOuForjadosSaoRejeitados() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        esqueci(usuario.getEmail());
        String token = emails.ultimoTokenPara(usuario.getEmail()).orElseThrow();
        jdbc.update("update password_token set expires_at = now() - interval '1 minute' where user_id = ?", usuario.getId());

        mvc.perform(post("/api/v1/auth/senha/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", token))))
                .andExpect(status().isGone());
        mvc.perform(post("/api/v1/auth/senha/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", "inventado"))))
                .andExpect(status().isGone());
    }

    @Test
    void pedidosDeRedefinicaoTemLimite() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        for (int i = 0; i < 5; i++) {
            esqueci(usuario.getEmail());
        }
        assertThat(emails.para(usuario.getEmail())).hasSize(3);
    }

    @Test
    void usuarioInativoNaoRecebeLinkDeRedefinicao() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        jdbc.update("update app_user set active = false where id = ?", usuario.getId());
        esqueci(usuario.getEmail());
        assertThat(emails.para(usuario.getEmail())).isEmpty();
    }

    @Test
    void alterarSenhaExigeSenhaAtualEMantemSessaoAtual() throws Exception {
        Usuario usuario = data.adminAdministradora(data.administradora());
        MvcResult outra = login(usuario.getEmail(), TestData.SENHA);
        Cookie outraSessao = outra.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        MvcResult atual = login(usuario.getEmail(), TestData.SENHA);
        Cookie sessaoAtual = atual.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        String token = "Bearer " + new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(atual.getResponse().getContentAsString()).get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/senha/alterar").header("Authorization", token).cookie(sessaoAtual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("senhaAtual", "errada12345", "novaSenha", "NovaSenha2026"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("WRONG_CURRENT_PASSWORD"));

        mvc.perform(post("/api/v1/auth/senha/alterar").header("Authorization", token).cookie(sessaoAtual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("senhaAtual", TestData.SENHA, "novaSenha", "NovaSenha2026"))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(sessaoAtual).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").cookie(outraSessao).header(AuthController.CLIENT_HEADER, "web"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/senha/alterar").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("senhaAtual", "x", "novaSenha", "NovaSenha2026"))))
                .andExpect(status().isUnauthorized());
    }
}
