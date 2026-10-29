package br.com.tekcondo.relatorio;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelatoriosPainelPortalIT extends IntegrationTest {

    static final String CONTA_TAXA_CONDOMINIAL = "20000000-0000-0000-0000-000000000101";
    static final String CONTA_MANUTENCAO = "20000000-0000-0000-0000-000000000205";

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    GravacaoEmailGateway email;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;
    String banco;
    String unidadeProprietario;
    String outraUnidade;
    LocalDate hoje = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Relatórios");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId();
        call(token, "PUT", base + "/financeiro/parametros", Map.of("percentualMulta", "2", "percentualJurosMensal", "1",
                "diaVencimento", 10, "exigeAprovacaoContasPagar", false), 200);
        banco = call(token, "POST", base + "/financeiro/contas-bancarias", Map.of("nome", "Banco", "tipo", "CHECKING",
                "saldoInicial", "1000.00", "dataAbertura", hoje.minusYears(1).toString()), 201).get("id").asText();
        unidadeProprietario = call(token, "POST", base + "/unidades", Map.of("identificacao", "11", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        outraUnidade = call(token, "POST", base + "/unidades", Map.of("identificacao", "12", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();

        Map<String, Object> despesa = new HashMap<>(Map.of("natureza", "EXPENSE", "descricao", "Conserto bomba",
                "idContaPlano", CONTA_MANUTENCAO, "valor", "300.00", "dataVencimento", hoje.toString()));
        String idDespesa = call(token, "POST", base + "/financeiro/lancamentos", despesa, 201).get(0).get("resumo").get("id").asText();
        call(token, "POST", base + "/financeiro/lancamentos/" + idDespesa + "/baixas", Map.of("idContaBancaria", banco,
                "dataPagamento", hoje.toString(), "principal", "300.00", "formaPagamento", "PIX"), 200);
        call(token, "POST", base + "/cobranca/rateios", Map.of("competencia", YearMonth.from(hoje).toString(),
                "descricao", "Taxa " + YearMonth.from(hoje), "idContaPlano", CONTA_TAXA_CONDOMINIAL, "criterio", "IDEAL_FRACTION",
                "valor", "800.00", "dataVencimento", hoje.plusDays(5).toString()), 201);
    }

    private JsonNode call(String auth, String method, String url, Object body, int expected) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", auth);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    @Test
    void todosOsRelatoriosGeramEExportamEmTodosOsFormatos() throws Exception {
        for (RelatorioService.Tipo tipo : RelatorioService.Tipo.values()) {
            String url = base + "/relatorios/" + tipo.chave() + "?de=" + hoje.withDayOfMonth(1) + "&ate=" + hoje.plusDays(10)
                    + (tipo == RelatorioService.Tipo.BANK_MOVEMENT ? "&idContaBancaria=" + banco : "");
            JsonNode relatorio = call(token, "GET", url, null, 200);
            assertThat(relatorio.get("titulo").asText()).as(tipo.name()).isNotBlank();
        }
        JsonNode receitasDespesas = call(token, "GET", base + "/relatorios/receitas-despesas", null, 200);
        assertThat(receitasDespesas.get("totais").get(3).decimalValue()).isEqualByComparingTo("-300.00");
        call(token, "GET", base + "/relatorios/INCOME_EXPENSE", null, 400); // a URL usa a chave pública, não o código interno

        byte[] csv = mvc.perform(get(base + "/relatorios/contas-pagar/exportar").param("formato", "CSV").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texto = new String(csv, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿Vencimento;").contains("300,00");
        byte[] xlsx = mvc.perform(get(base + "/relatorios/fluxo-caixa/exportar").param("formato", "XLSX").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        byte[] pdf = mvc.perform(get(base + "/relatorios/inadimplencia/exportar").param("formato", "PDF").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'REPORT_EXPORTED' and entity_id = ?",
                Integer.class, condominio.getId().toString())).isEqualTo(3);

        Usuario equipe = data.usuario(administradora);
        data.conceder(equipe, "CONDO_STAFF", condominio.getId());
        mvc.perform(get(base + "/relatorios/receitas-despesas").header("Authorization", TestData.bearer(data.login(equipe))))
                .andExpect(status().isForbidden());
    }

    @Test
    void painelConsolidaApenasCondominiosAcessiveis() throws Exception {
        JsonNode p = call(token, "GET", "/api/v1/painel", null, 200);
        assertThat(p.get("condominios").asInt()).isEqualTo(1);
        assertThat(p.get("unidades").asInt()).isEqualTo(2);
        assertThat(p.get("despesas").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(p.get("saldo").decimalValue()).isEqualByComparingTo("700.00");
        assertThat(p.get("aReceberAberto").decimalValue()).isEqualByComparingTo("800.00");
        assertThat(p.get("cobrancasEmitidas").asInt()).isEqualTo(2);
        assertThat(p.get("serie")).hasSize(12);

        Condominio outro = data.condominio(administradora, "Outro");
        Usuario sindico = data.usuario(administradora);
        data.conceder(sindico, "SYNDIC", outro.getId());
        JsonNode restrito = call(TestData.bearer(data.login(sindico)), "GET", "/api/v1/painel", null, 200);
        assertThat(restrito.get("condominios").asInt()).isEqualTo(1);
        assertThat(restrito.get("unidades").asInt()).isZero();
        call(TestData.bearer(data.login(sindico)), "GET", "/api/v1/painel?idCondominio=" + condominio.getId(), null, 404);
    }

    @Test
    void portalDoMoradorMostraApenasOsPropriosDados() throws Exception {
        String emailProprietario = TestData.emailUnico();
        String proprietario = call(token, "POST", "/api/v1/pessoas", Map.of("nome", "Olga Dona", "tipo", "INDIVIDUAL", "email", emailProprietario), 201).get("id").asText();
        String emailInquilino = TestData.emailUnico();
        String inquilino = call(token, "POST", "/api/v1/pessoas", Map.of("nome", "Ivo Morador", "tipo", "INDIVIDUAL", "email", emailInquilino), 201).get("id").asText();
        call(token, "POST", base + "/vinculos", Map.of("idUnidade", unidadeProprietario, "idPessoa", proprietario, "tipo", "OWNER",
                "responsavelFinanceiro", true, "dataInicio", "2024-01-01"), 201);
        call(token, "POST", base + "/vinculos", Map.of("idUnidade", unidadeProprietario, "idPessoa", inquilino, "tipo", "RESIDENT",
                "responsavelFinanceiro", false, "dataInicio", "2024-01-01"), 201);
        call(token, "POST", "/api/v1/pessoas/" + proprietario + "/acesso-portal", null, 200);
        call(token, "POST", "/api/v1/pessoas/" + inquilino + "/acesso-portal", null, 200);
        String tokenProprietario = ativar(emailProprietario);
        String tokenInquilino = ativar(emailInquilino);

        JsonNode perfil = call(tokenProprietario, "GET", "/api/v1/portal/perfil", null, 200);
        assertThat(perfil.get("condominios").get(0).get("unidades").get(0).get("rotulo").asText()).isEqualTo("11");
        String p = "/api/v1/portal/" + condominio.getId();
        JsonNode cobrancas = call(tokenProprietario, "GET", p + "/cobrancas", null, 200);
        assertThat(cobrancas).hasSize(1);
        assertThat(cobrancas.get(0).get("totalAberto").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(call(tokenInquilino, "GET", p + "/cobrancas", null, 200)).isEmpty();

        byte[] pdf = "%PDF-1.4 regulamento".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart(base + "/documentos").file(new MockMultipartFile("arquivo", "regulamento.pdf", "application/pdf", pdf))
                .param("categoria", "INTERNAL_RULES").param("titulo", "Regulamento").param("visibilidade", "RESIDENTS")
                .header("Authorization", token)).andExpect(status().isCreated());
        mvc.perform(multipart(base + "/documentos").file(new MockMultipartFile("arquivo", "folha.pdf", "application/pdf", pdf))
                .param("categoria", "OTHER").param("titulo", "Folha de pagamento").header("Authorization", token)).andExpect(status().isCreated());
        JsonNode documentos = call(tokenInquilino, "GET", p + "/documentos", null, 200);
        assertThat(documentos).hasSize(1);
        assertThat(documentos.get(0).get("titulo").asText()).isEqualTo("Regulamento");

        String ocorrencia = call(tokenInquilino, "POST", p + "/ocorrencias", Map.of("titulo", "Lâmpada queimada", "descricao", "Corredor do 1º andar",
                "categoria", "MAINTENANCE", "prioridade", "URGENT", "idUnidade", unidadeProprietario), 201).get("resumo").get("id").asText();
        call(tokenInquilino, "POST", p + "/ocorrencias", Map.of("titulo", "x", "descricao", "x", "categoria", "OTHER", "idUnidade", outraUnidade), 403);
        call(token, "POST", base + "/ocorrencias/" + ocorrencia + "/interacoes", Map.of("mensagem", "Nota interna da equipe", "interna", true), 200);
        call(token, "POST", base + "/ocorrencias/" + ocorrencia + "/interacoes", Map.of("mensagem", "Eletricista agendado", "interna", false), 200);
        JsonNode vista = call(tokenInquilino, "GET", p + "/ocorrencias/" + ocorrencia, null, 200);
        assertThat(vista.get("resumo").get("prioridade").asText()).isEqualTo("MEDIUM");
        assertThat(vista.get("interacoes")).hasSize(1);
        assertThat(vista.get("interacoes").get(0).get("mensagem").asText()).isEqualTo("Eletricista agendado");

        assertThat(call(tokenProprietario, "GET", p + "/calendario", null, 200).get(0).get("tipo").asText()).isEqualTo("CHARGE");
        call(token, "GET", "/api/v1/portal/perfil", null, 403); // administrador não é morador
        call(tokenProprietario, "GET", base + "/financeiro/lancamentos", null, 403); // morador não acessa a área administrativa
    }

    private String ativar(String emailMorador) throws Exception {
        String convite = email.ultimoTokenPara(emailMorador).orElseThrow();
        call("", "POST", "/api/v1/auth/senha/redefinir", Map.of("token", convite, "novaSenha", "MoradorSenha2026"), 204);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(data.json(Map.of("email", emailMorador, "senha", "MoradorSenha2026")))).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
    }
}
