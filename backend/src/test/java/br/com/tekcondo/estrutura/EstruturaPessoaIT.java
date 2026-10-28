package br.com.tekcondo.estrutura;

import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstruturaPessoaIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    GravacaoEmailGateway mail;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;

    @BeforeEach
    void setUp() {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Residencial Jatobá");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId();
    }

    private JsonNode postJson(String url, Object corpo, int esperado) throws Exception {
        String json = mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(corpo)))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private Map<String, Object> unidade(Object idBloco, String identificacao, String fracao) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("idBloco", idBloco);
        corpo.put("identificacao", identificacao);
        corpo.put("tipo", "APARTMENT");
        corpo.put("fracaoIdeal", fracao);
        return corpo;
    }

    @Test
    void blocosUnidadesEResumoDasFracoesIdeais() throws Exception {
        String blocoA = postJson(base + "/blocos", Map.of("nome", "Bloco A"), 201).get("id").asText();
        postJson(base + "/blocos", Map.of("nome", "bloco a"), 409);

        postJson(base + "/unidades", unidade(blocoA, "101", "60.5"), 201);
        postJson(base + "/unidades", unidade(blocoA, "101", "1"), 409);
        postJson(base + "/unidades", unidade(null, "101", "1"), 201); // mesmo número fora do bloco é outra unidade

        mvc.perform(get(base + "/unidades/resumo-fracoes").header("Authorization", token))
                .andExpect(jsonPath("$.total").value(61.5))
                .andExpect(jsonPath("$.fechada").value(false))
                .andExpect(jsonPath("$.diferenca").value(38.5));

        postJson(base + "/unidades", unidade(null, "Loja 1", "38.5"), 201);
        mvc.perform(get(base + "/unidades/resumo-fracoes").header("Authorization", token))
                .andExpect(jsonPath("$.fechada").value(true));

        postJson(base + "/unidades", unidade(null, "X", "100.5"), 400);
        postJson(base + "/blocos/" + blocoA + "/desativar", null, 422);
    }

    @Test
    void importacaoCsvETudoOuNadaEReaproveitaPessoasPorDocumento() throws Exception {
        String ruim = """
                bloco;unidade;tipo;fracao;area;proprietario;documento;email;telefone
                A;101;apartamento;50,0;72,5;Ana Souza;529.982.247-25;ana@ex.com;(91) 98765-4321
                A;102;nave;10;;;;;
                A;101;apartamento;1;;;;;
                """;
        mvc.perform(multipart(base + "/unidades/importar").file(csv(ruim)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadesCriadas").value(0))
                .andExpect(jsonPath("$.erros", hasSize(2)))
                .andExpect(jsonPath("$.erros[0].linha").value(3));
        assertThat(jdbc.queryForObject("select count(*) from unit where condominium_id = ?", Integer.class, condominio.getId())).isZero();

        String bom = """
                Bloco;Unidade;Tipo;Fração;Área;Proprietário;Documento;E-mail;Telefone
                A;101;apartamento;50,0;72,5;Ana Souza;529.982.247-25;ana@ex.com;(91) 98765-4321
                A;102;apto;25;;Ana Souza;52998224725;;
                ;Loja 1;loja;25;;Comércio XYZ Ltda;11.222.333/0001-81;;
                """;
        mvc.perform(multipart(base + "/unidades/importar").file(csv(bom)).param("simulacao", "true").header("Authorization", token))
                .andExpect(jsonPath("$.simulacao").value(true))
                .andExpect(jsonPath("$.unidadesCriadas").value(3));
        assertThat(jdbc.queryForObject("select count(*) from unit where condominium_id = ?", Integer.class, condominio.getId())).isZero();

        mvc.perform(multipart(base + "/unidades/importar").file(csv(bom)).header("Authorization", token))
                .andExpect(jsonPath("$.erros", hasSize(0)))
                .andExpect(jsonPath("$.unidadesCriadas").value(3))
                .andExpect(jsonPath("$.blocosCriados").value(1))
                .andExpect(jsonPath("$.pessoasCriadas").value(2))
                .andExpect(jsonPath("$.pessoasReaproveitadas").value(1));

        mvc.perform(get(base + "/unidades").header("Authorization", token).param("busca", "101"))
                .andExpect(jsonPath("$.conteudo[0].rotulo").value("A · 101"))
                .andExpect(jsonPath("$.conteudo[0].nomeProprietario").value("Ana Souza"))
                .andExpect(jsonPath("$.conteudo[0].nomeResponsavelFinanceiro").value("Ana Souza"));
        mvc.perform(get(base + "/unidades/resumo-fracoes").header("Authorization", token))
                .andExpect(jsonPath("$.fechada").value(true));
    }

    @Test
    void pessoasVinculosEResponsabilidadeFinanceira() throws Exception {
        String idUnidade = postJson(base + "/unidades", unidade(null, "201", "10"), 201).get("id").asText();
        String proprietario = postJson("/api/v1/pessoas", Map.of("nome", "Bruno Lima", "tipo", "INDIVIDUAL",
                "documento", "111.444.777-35", "email", "bruno@ex.com"), 201).get("id").asText();
        postJson("/api/v1/pessoas", Map.of("nome", "Outro", "tipo", "INDIVIDUAL", "documento", "11144477735"), 409);
        postJson("/api/v1/pessoas", Map.of("nome", "CPF ruim", "tipo", "INDIVIDUAL", "documento", "111.444.777-00"), 400);
        postJson("/api/v1/pessoas", Map.of("nome", "Empresa", "tipo", "COMPANY", "documento", "111.444.777-35"), 422);
        String inquilina = postJson("/api/v1/pessoas", Map.of("nome", "Carla Inquilina", "tipo", "INDIVIDUAL"), 201)
                .get("id").asText();

        postJson(base + "/vinculos", Map.of("idUnidade", idUnidade, "idPessoa", proprietario, "tipo", "OWNER",
                "responsavelFinanceiro", true, "dataInicio", "2024-01-01"), 201);
        postJson(base + "/vinculos", Map.of("idUnidade", idUnidade, "idPessoa", inquilina, "tipo", "TENANT",
                "responsavelFinanceiro", true, "dataInicio", "2026-01-01"), 409);
        String locacao = postJson(base + "/vinculos", Map.of("idUnidade", idUnidade, "idPessoa", inquilina, "tipo", "TENANT",
                "responsavelFinanceiro", false, "dataInicio", "2026-01-01"), 201).get("id").asText();

        postJson(base + "/vinculos/" + locacao + "/responsavel-financeiro", null, 200);
        mvc.perform(get(base + "/unidades/" + idUnidade).header("Authorization", token))
                .andExpect(jsonPath("$.nomeResponsavelFinanceiro").value("Carla Inquilina"));

        postJson(base + "/vinculos/" + locacao + "/encerrar", Map.of("dataFim", "2025-12-01"), 422);
        postJson(base + "/vinculos/" + locacao + "/encerrar", Map.of("dataFim", "2026-06-30", "motivo", "Fim do contrato"), 200);

        mvc.perform(get(base + "/unidades/" + idUnidade + "/vinculos").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].motivoEncerramento").value("Fim do contrato"));
        mvc.perform(get("/api/v1/pessoas").header("Authorization", token).param("busca", "529.982"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/pessoas").header("Authorization", token).param("busca", "111444"))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Bruno Lima"));
    }

    @Test
    void acessoAoPortalCriaUsuarioVinculadoComPapelDeProprietario() throws Exception {
        String idUnidade = postJson(base + "/unidades", unidade(null, "301", "10"), 201).get("id").asText();
        String email = TestData.emailUnico();
        String pessoa = postJson("/api/v1/pessoas", Map.of("nome", "Dora Dona", "tipo", "INDIVIDUAL", "email", email), 201)
                .get("id").asText();
        postJson("/api/v1/pessoas/" + pessoa + "/acesso-portal", null, 422); // sem vínculo
        postJson(base + "/vinculos", Map.of("idUnidade", idUnidade, "idPessoa", pessoa, "tipo", "OWNER",
                "responsavelFinanceiro", true, "dataInicio", "2025-01-01"), 201);

        postJson("/api/v1/pessoas/" + pessoa + "/acesso-portal", null, 200);
        assertThat(mail.ultimoTokenPara(email)).isPresent();
        assertThat(jdbc.queryForObject("""
                select r.code from user_role ur join role r on r.id = ur.role_id join app_user u on u.id = ur.user_id
                where u.person_id = ?::uuid""", String.class, pessoa)).isEqualTo("OWNER");
    }

    @Test
    void usuarioComEscopoSoVeSeuCondominioEOutrasAdministradorasSaoInvisiveis() throws Exception {
        String idUnidade = postJson(base + "/unidades", unidade(null, "401", "10"), 201).get("id").asText();
        String pessoa = postJson("/api/v1/pessoas", Map.of("nome", "Elisa Moradora", "tipo", "INDIVIDUAL"), 201).get("id").asText();
        postJson(base + "/vinculos", Map.of("idUnidade", idUnidade, "idPessoa", pessoa, "tipo", "RESIDENT",
                "responsavelFinanceiro", false, "dataInicio", "2025-01-01"), 201);
        postJson("/api/v1/pessoas", Map.of("nome", "Sem Vínculo", "tipo", "INDIVIDUAL"), 201);

        Condominio outro = data.condominio(administradora, "Outro");
        Usuario sindico = data.usuario(administradora);
        data.conceder(sindico, "SYNDIC", outro.getId());
        String tokenSindico = TestData.bearer(data.login(sindico));
        mvc.perform(get("/api/v1/pessoas").header("Authorization", tokenSindico))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get(base + "/unidades").header("Authorization", tokenSindico)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/pessoas/" + pessoa).header("Authorization", tokenSindico)).andExpect(status().isNotFound());

        String tokenOutraAdministradora = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/unidades").header("Authorization", tokenOutraAdministradora)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/pessoas/" + pessoa).header("Authorization", tokenOutraAdministradora)).andExpect(status().isNotFound());
    }

    @Test
    void condominiosAcessiveisListamPermissoes() throws Exception {
        mvc.perform(get("/api/v1/condominios/acessiveis").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Residencial Jatobá"))
                .andExpect(jsonPath("$[0].permissoes").isArray());
    }

    private static MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("arquivo", "unidades.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }
}
