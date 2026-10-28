package br.com.tekcondo.condominio;

import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CondominioIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;

    String token;

    @BeforeEach
    void setUp() {
        Administradora administradora = data.administradora();
        Usuario admin = data.adminAdministradora(administradora);
        token = TestData.bearer(data.login(admin));
    }

    private Map<String, Object> corpoValido() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Residencial Jardim das Flores");
        corpo.put("razaoSocial", "Condomínio Edifício Jardim das Flores");
        corpo.put("cnpj", "12.ABC.345/01DE-35");
        corpo.put("tipo", "RESIDENTIAL");
        corpo.put("email", "sindico@jardim.com.br");
        corpo.put("telefone", "(11) 98765-4321");
        corpo.put("endereco", Map.of("cep", "01310-100", "logradouro", "Av. Paulista", "numero", "1000",
                "bairro", "Bela Vista", "cidade", "São Paulo", "uf", "sp"));
        return corpo;
    }

    private JsonNode cadastrar(Map<String, Object> corpo) throws Exception {
        String json = mvc.perform(post("/api/v1/condominios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void cadastraEConsultaCondominioComDadosNormalizados() throws Exception {
        String corpo = mvc.perform(post("/api/v1/condominios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpoValido())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.cnpj").value("12ABC34501DE35"))
                .andExpect(jsonPath("$.telefone").value("11987654321"))
                .andExpect(jsonPath("$.endereco.cep").value("01310100"))
                .andExpect(jsonPath("$.endereco.uf").value("SP"))
                .andExpect(jsonPath("$.situacao").value("ACTIVE"))
                .andExpect(jsonPath("$.versao").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(corpo).get("id").asText();

        mvc.perform(get("/api/v1/condominios/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Residencial Jardim das Flores"));

        Integer auditorias = jdbc.queryForObject(
                "select count(*) from audit_log where entity_id = ? and action = 'CONDOMINIUM_CREATED'", Integer.class, id);
        assertThat(auditorias).isEqualTo(1);
    }

    @Test
    void errosDeValidacaoVoltamPorCampoEmPortugues() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("nome", "");
        corpo.put("cnpj", "11.111.111/1111-11");
        corpo.put("tipo", null);
        corpo.put("endereco", Map.of("cep", "123", "uf", "XX"));

        mvc.perform(post("/api/v1/condominios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("nome")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("cnpj")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("tipo")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("endereco.cep")))
                .andExpect(jsonPath("$.erros[*].campo", hasItem("endereco.uf")))
                .andExpect(jsonPath("$.erros[*].mensagem", hasItem("CNPJ inválido.")));
    }

    @Test
    void cnpjDuplicadoNaMesmaAdministradoraERejeitado() throws Exception {
        cadastrar(corpoValido());

        mvc.perform(post("/api/v1/condominios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(corpoValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONDOMINIUM_CNPJ_TAKEN"));
    }

    @Test
    void atualizacaoUsaBloqueioOtimistaEAuditaAlteracoes() throws Exception {
        JsonNode criado = cadastrar(corpoValido());
        String id = criado.get("id").asText();

        Map<String, Object> atualizacao = corpoValido();
        atualizacao.put("nome", "Residencial Jardim Renovado");
        atualizacao.put("versao", 0);
        mvc.perform(put("/api/v1/condominios/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(atualizacao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Residencial Jardim Renovado"))
                .andExpect(jsonPath("$.versao").value(1));

        // Segunda edição com a versão antiga: outro usuário já alterou o registro.
        mvc.perform(put("/api/v1/condominios/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(atualizacao)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STALE_VERSION"));

        String detalhes = jdbc.queryForObject(
                "select details::text from audit_log where entity_id = ? and action = 'CONDOMINIUM_UPDATED'", String.class, id);
        assertThat(detalhes).contains("Residencial Jardim Renovado").contains("Residencial Jardim das Flores");
    }

    @Test
    void inativarEAtivarPreservamHistorico() throws Exception {
        String id = cadastrar(corpoValido()).get("id").asText();

        mvc.perform(post("/api/v1/condominios/" + id + "/inativar").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("motivo", "x"))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/condominios/" + id + "/inativar").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(Map.of("motivo", "Fim do contrato de administração"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("INACTIVE"))
                .andExpect(jsonPath("$.motivoInativacao").value("Fim do contrato de administração"));

        Map<String, Object> atualizacao = corpoValido();
        atualizacao.put("versao", 1);
        mvc.perform(put("/api/v1/condominios/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(data.json(atualizacao)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONDOMINIUM_INACTIVE"));

        mvc.perform(post("/api/v1/condominios/" + id + "/ativar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ACTIVE"));
    }

    @Test
    void listagemSuportaBuscaFiltroDeSituacaoPaginacaoEOrdenacao() throws Exception {
        for (String nome : new String[]{"Edifício Bravo", "Edifício Alfa", "Condomínio Charlie"}) {
            Map<String, Object> corpo = corpoValido();
            corpo.put("nome", nome);
            corpo.remove("cnpj");
            corpo.remove("razaoSocial");
            cadastrar(corpo);
        }

        mvc.perform(get("/api/v1/condominios").header("Authorization", token)
                        .param("busca", "edifício").param("ordem", "nome,asc").param("tamanho", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Edifício Alfa"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        mvc.perform(get("/api/v1/condominios").header("Authorization", token).param("situacao", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));

        mvc.perform(get("/api/v1/condominios").header("Authorization", token).param("ordem", "password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("INVALID_SORT"));
    }

    @Test
    void buscaPorCnpjComMascaraEncontraCondominio() throws Exception {
        cadastrar(corpoValido());

        mvc.perform(get("/api/v1/condominios").header("Authorization", token).param("busca", "12.abc.345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }
}
