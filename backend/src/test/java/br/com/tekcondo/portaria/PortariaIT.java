package br.com.tekcondo.portaria;

import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortariaIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    GravacaoEmailGateway email;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PortariaService portariaService;

    Administradora administradora;
    Condominio condominio;
    String admin;
    String porteiro;
    String morador;
    String base;
    String portal;
    String unidade101;
    String unidade102;

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Portaria");
        admin = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        Usuario equipe = data.usuario(administradora);
        data.conceder(equipe, "CONDO_STAFF", condominio.getId());
        porteiro = TestData.bearer(data.login(equipe));
        base = "/api/v1/condominios/" + condominio.getId();
        portal = "/api/v1/portal/" + condominio.getId();
        unidade101 = call(admin, "POST", base + "/unidades", Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        unidade102 = call(admin, "POST", base + "/unidades", Map.of("identificacao", "102", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        String emailMorador = TestData.emailUnico();
        String pessoa = call(admin, "POST", "/api/v1/pessoas", Map.of("nome", "Caio Morador", "tipo", "INDIVIDUAL", "email", emailMorador), 201)
                .get("id").asText();
        call(admin, "POST", base + "/vinculos", Map.of("idUnidade", unidade101, "idPessoa", pessoa, "tipo", "TENANT",
                "responsavelFinanceiro", false, "dataInicio", "2024-01-01"), 201);
        call(admin, "POST", "/api/v1/pessoas/" + pessoa + "/acesso-portal", null, 200);
        morador = ativar(emailMorador);
    }

    private JsonNode call(String auth, String method, String url, Object body, int expected) throws Exception {
        var builder = "POST".equals(method) ? post(url) : get(url);
        if (!auth.isEmpty()) builder.header("Authorization", auth);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private String ativar(String emailMorador) throws Exception {
        String convite = email.ultimoTokenPara(emailMorador).orElseThrow();
        call("", "POST", "/api/v1/auth/senha/redefinir", Map.of("token", convite, "novaSenha", "MoradorSenha2026"), 204);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", emailMorador, "senha", "MoradorSenha2026")))).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
    }

    @Test
    void moradorPreAutorizaEPortariaRegistraEntradaPeloCodigo() throws Exception {
        call(morador, "POST", portal + "/visitantes", Map.of("idUnidade", unidade102, "tipo", "VISITOR", "nome", "Fulano"), 403);
        JsonNode autorizacao = call(morador, "POST", portal + "/visitantes", Map.of("idUnidade", unidade101, "tipo", "SERVICE_PROVIDER",
                "nome", "Pedro Eletricista", "documento", "123.456.789-09", "empresa", "Luz Ltda", "placaVeiculo", "ABC-1D23"), 201);
        String codigo = autorizacao.get("codigoAcesso").asText();
        assertThat(codigo).matches("\\d{6}");
        assertThat(autorizacao.get("documento").asText()).isEqualTo("********909");
        assertThat(autorizacao.get("placaVeiculo").asText()).isEqualTo("ABC1D23");

        JsonNode hoje = call(porteiro, "GET", base + "/portaria/hoje", null, 200);
        assertThat(hoje).hasSize(1);
        assertThat(hoje.get(0).has("codigoAcesso")).isFalse(); // a portaria não vê o código

        call(porteiro, "POST", base + "/portaria/entrada-por-codigo", Map.of("codigo", "12345"), 400);
        JsonNode dentro = call(porteiro, "POST", base + "/portaria/entrada-por-codigo", Map.of("codigo", codigo), 200);
        assertThat(dentro.get("situacao").asText()).isEqualTo("INSIDE");
        call(porteiro, "POST", base + "/portaria/entrada-por-codigo", Map.of("codigo", codigo), 404); // código de uso único

        JsonNode caixaEntrada = call(morador, "GET", "/api/v1/notificacoes", null, 200);
        assertThat(caixaEntrada.get("itens").get(0).get("titulo").asText()).isEqualTo("Prestador de serviço na portaria");

        JsonNode saiu = call(porteiro, "POST", base + "/portaria/" + autorizacao.get("id").asText() + "/saida", null, 200);
        assertThat(saiu.get("situacao").asText()).isEqualTo("LEFT");

        // Chegada sem aviso: registrada pela portaria com entrada imediata.
        JsonNode semAviso = call(porteiro, "POST", base + "/portaria/chegadas", Map.of("idUnidade", unidade102, "tipo", "DELIVERY", "nome", "Entregador"), 201);
        assertThat(semAviso.get("situacao").asText()).isEqualTo("INSIDE");
        assertThat(call(morador, "GET", portal + "/visitantes", null, 200)).hasSize(1); // morador só vê a própria unidade
        assertThat(call(porteiro, "GET", base + "/portaria/historico", null, 200)).hasSize(2);
    }

    @Test
    void permissoesIsolamentoERetencao() throws Exception {
        Usuario conselheiro = data.usuario(administradora);
        data.conceder(conselheiro, "COUNCIL", condominio.getId());
        String tokenConselho = TestData.bearer(data.login(conselheiro));
        call(tokenConselho, "GET", base + "/portaria/hoje", null, 200);
        call(tokenConselho, "POST", base + "/portaria/chegadas", Map.of("idUnidade", unidade101, "tipo", "VISITOR", "nome", "X"), 403);
        call(TestData.bearer(data.login(data.adminAdministradora(data.administradora()))), "GET", base + "/portaria/hoje", null, 404);

        String id = call(morador, "POST", portal + "/visitantes", Map.of("idUnidade", unidade101, "tipo", "VISITOR", "nome", "Maria Visita",
                "documento", "MG1234567"), 201).get("id").asText();
        call(morador, "POST", portal + "/visitantes/" + id + "/cancelar", null, 200);
        jdbc.update("update visitor_pass set valid_from = ?, valid_until = ? where id = ?::uuid",
                LocalDate.now().minusDays(400), LocalDate.now().minusDays(400), id);
        assertThat(portariaService.anonimizarAnterioresA(180)).isGreaterThanOrEqualTo(1);
        Map<String, Object> registro = jdbc.queryForMap("select name, document, anonymized_at from visitor_pass where id = ?::uuid", id);
        assertThat(registro.get("name")).isEqualTo("[anonimizado]");
        assertThat(registro.get("document")).isNull();
        assertThat(registro.get("anonymized_at")).isNotNull();
    }
}
