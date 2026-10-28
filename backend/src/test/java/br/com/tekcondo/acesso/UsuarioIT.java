package br.com.tekcondo.acesso;

import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.GravacaoEmailGateway;
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

class UsuarioIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    GravacaoEmailGateway emails;
    @Autowired
    JdbcTemplate jdbc;

    Administradora org;
    Usuario administrador;
    Condominio condominio;
    String token;

    @BeforeEach
    void setUp() {
        org = data.administradora();
        administrador = data.adminAdministradora(org);
        condominio = data.condominio(org, "Residencial Sabiá");
        token = TestData.bearer(data.login(administrador));
    }

    private Map<String, Object> corpoCadastro(String email, String codigoPapel, Object idCondominio) {
        Map<String, Object> atribuicao = new HashMap<>();
        atribuicao.put("codigoPapel", codigoPapel);
        atribuicao.put("idCondominio", idCondominio);
        return Map.of("nome", "Carlos Síndico", "email", email, "atribuicao", atribuicao);
    }

    private JsonNode cadastrarUsuario(String email, String codigoPapel, Object idCondominio) throws Exception {
        String json = mvc.perform(post("/api/v1/usuarios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpoCadastro(email, codigoPapel, idCondominio))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void usuarioCadastradoRecebeConviteDefineSenhaEEntraComAcessoRestrito() throws Exception {
        String email = TestData.emailUnico();
        JsonNode criado = cadastrarUsuario(email, "SYNDIC", condominio.getId());
        assertThat(criado.get("convitePendente").asBoolean()).isTrue();
        assertThat(criado.get("atribuicoes").get(0).get("nomeCondominio").asText()).isEqualTo("Residencial Sabiá");

        // Sem senha definida, não consegue entrar.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", email, "senha", TestData.SENHA))))
                .andExpect(status().isUnauthorized());

        String convite = emails.ultimoTokenPara(email).orElseThrow();
        assertThat(emails.para(email).getLast().assunto()).contains("Convite");
        mvc.perform(post("/api/v1/auth/senha/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", convite))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalidade").value("INVITE"))
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/v1/auth/senha/redefinir").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", convite, "novaSenha", "MinhaSenha2026"))))
                .andExpect(status().isNoContent());

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("email", email, "senha", "MinhaSenha2026"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String tokenSindico = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/v1/condominios").header("Authorization", tokenSindico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(1)));
        // Síndico não gerencia usuários.
        mvc.perform(get("/api/v1/usuarios").header("Authorization", tokenSindico)).andExpect(status().isForbidden());
    }

    @Test
    void listaEBuscaSomenteUsuariosDaPropriaAdministradora() throws Exception {
        cadastrarUsuario(TestData.emailUnico(), "ORG_STAFF", null);
        Usuario deFora = data.adminAdministradora(data.administradora());

        mvc.perform(get("/api/v1/usuarios").header("Authorization", token).param("busca", "carlos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].papeis", hasItem("Funcionário da administradora")));

        mvc.perform(get("/api/v1/usuarios/" + deFora.getId()).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void escopoDoPapelEValidado() throws Exception {
        mvc.perform(post("/api/v1/usuarios").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(corpoCadastro(TestData.emailUnico(), "SYNDIC", null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ROLE_SCOPE_MISMATCH"));
        mvc.perform(post("/api/v1/usuarios").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(corpoCadastro(TestData.emailUnico(), "ORG_ADMIN", condominio.getId()))))
                .andExpect(status().isUnprocessableEntity());

        Condominio deOutra = data.condominio(data.administradora(), "Condomínio de outra administradora");
        mvc.perform(post("/api/v1/usuarios").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(corpoCadastro(TestData.emailUnico(), "SYNDIC", deOutra.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void emailEAtribuicaoDuplicadosSaoRejeitados() throws Exception {
        String email = TestData.emailUnico();
        String id = cadastrarUsuario(email, "COUNCIL", condominio.getId()).get("id").asText();

        mvc.perform(post("/api/v1/usuarios").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(corpoCadastro(email.toUpperCase(), "COUNCIL", condominio.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USER_EMAIL_TAKEN"));

        mvc.perform(post("/api/v1/usuarios/" + id + "/atribuicoes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("codigoPapel", "COUNCIL", "idCondominio", condominio.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ROLE_ALREADY_ASSIGNED"));
    }

    @Test
    void atribuicaoERevogacaoDePapeisSaoAuditadas() throws Exception {
        String id = cadastrarUsuario(TestData.emailUnico(), "ORG_STAFF", null).get("id").asText();

        String detalhe = mvc.perform(post("/api/v1/usuarios/" + id + "/atribuicoes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("codigoPapel", "SYNDIC", "idCondominio", condominio.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atribuicoes", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String idAtribuicao = null;
        for (JsonNode a : objectMapper.readTree(detalhe).get("atribuicoes")) {
            if (a.get("codigoPapel").asText().equals("SYNDIC")) {
                idAtribuicao = a.get("id").asText();
            }
        }

        mvc.perform(delete("/api/v1/usuarios/" + id + "/atribuicoes/" + idAtribuicao).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atribuicoes", hasSize(1)));

        Integer audits = jdbc.queryForObject("""
                select count(*) from audit_log where entity_id = ? and action in ('ROLE_ASSIGNED', 'ROLE_REVOKED')""",
                Integer.class, id);
        assertThat(audits).isEqualTo(3);
    }

    @Test
    void administradoraSempreMantemUmAdministradorAtivo() throws Exception {
        // Único administrador não pode remover o próprio papel de administrador.
        String detalhe = mvc.perform(get("/api/v1/usuarios/" + administrador.getId()).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String atribuicaoAdministrador = objectMapper.readTree(detalhe).get("atribuicoes").get(0).get("id").asText();
        mvc.perform(delete("/api/v1/usuarios/" + administrador.getId() + "/atribuicoes/" + atribuicaoAdministrador).header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LAST_ADMIN"));

        mvc.perform(post("/api/v1/usuarios/" + administrador.getId() + "/desativar").header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    void usuarioDesativadoPerdeAcessoImediatamente() throws Exception {
        Usuario funcionario = data.usuario(org);
        data.conceder(funcionario, "ORG_STAFF", null);
        String tokenFuncionario = TestData.bearer(data.login(funcionario));
        mvc.perform(get("/api/v1/condominios").header("Authorization", tokenFuncionario)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/usuarios/" + funcionario.getId() + "/desativar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        // Token de acesso ainda não expirou, mas as permissões são lidas do banco: acesso negado na hora.
        mvc.perform(get("/api/v1/condominios").header("Authorization", tokenFuncionario)).andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/usuarios/" + funcionario.getId() + "/ativar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void atualizarNomeUsaBloqueioOtimista() throws Exception {
        String id = cadastrarUsuario(TestData.emailUnico(), "ORG_STAFF", null).get("id").asText();

        mvc.perform(put("/api/v1/usuarios/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("nome", "Carlos Souza", "versao", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Carlos Souza"));
        mvc.perform(put("/api/v1/usuarios/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("nome", "Outro Nome", "versao", 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void catalogoDePapeisExpoeEscopo() throws Exception {
        mvc.perform(get("/api/v1/papeis").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'ORG_ADMIN')].escopo").value(hasItem("ORGANIZATION")))
                .andExpect(jsonPath("$[?(@.codigo == 'SYNDIC')].escopo").value(hasItem("CONDOMINIUM")));
    }

    @Test
    void reenviarConviteSomenteParaUsuariosPendentes() throws Exception {
        String email = TestData.emailUnico();
        String id = cadastrarUsuario(email, "ORG_STAFF", null).get("id").asText();
        String primeiro = emails.ultimoTokenPara(email).orElseThrow();

        mvc.perform(post("/api/v1/usuarios/" + id + "/convite").header("Authorization", token)).andExpect(status().isAccepted());
        String segundo = emails.ultimoTokenPara(email).orElseThrow();
        assertThat(segundo).isNotEqualTo(primeiro);

        // O convite anterior foi invalidado pelo novo.
        mvc.perform(post("/api/v1/auth/senha/token").contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(Map.of("token", primeiro))))
                .andExpect(status().isGone());

        mvc.perform(post("/api/v1/usuarios/" + administrador.getId() + "/convite").header("Authorization", token))
                .andExpect(status().isUnprocessableEntity());
    }
}
