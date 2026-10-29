package br.com.tekcondo.operacao;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import br.com.tekcondo.comunicado.ComunicadoRepository;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.rotina.LembretesDiariosJob;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.fornecedor.repository.ContratoRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperacoesIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    GravacaoEmailGateway email;
    @Autowired
    ContratoRepository contratoRepository;
    @Autowired
    LancamentoRepository lancamentoRepository;
    @Autowired
    ComunicadoRepository comunicadoRepository;
    @Autowired
    NotificacaoService notificacaoService;
    @Autowired
    TransactionTemplate tx;

    Administradora administradora;
    Condominio condominio;
    Usuario admin;
    String token;
    String base;
    String unidade101;
    String unidade102;

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Operações");
        admin = data.adminAdministradora(administradora);
        token = TestData.bearer(data.login(admin));
        base = "/api/v1/condominios/" + condominio.getId();
        unidade101 = call("POST", base + "/unidades", Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "60"), 201).get("id").asText();
        unidade102 = call("POST", base + "/unidades", Map.of("identificacao", "102", "tipo", "APARTMENT", "fracaoIdeal", "40"), 201).get("id").asText();
    }

    private JsonNode call(String method, String url, Object body, int expected) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(data.json(body));
        }
        String json = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    @Test
    void documentosValidamConteudoESoSaoServidosPelaApi() throws Exception {
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        String id = objectMapper.readTree(mvc.perform(multipart(base + "/documentos")
                        .file(new MockMultipartFile("arquivo", "convencao.pdf", "application/pdf", pdf))
                        .param("categoria", "CONVENTION").param("titulo", "Convenção condominial").param("visibilidade", "RESIDENTS")
                        .header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(multipart(base + "/documentos")
                        .file(new MockMultipartFile("arquivo", "virus.pdf", "application/pdf", "MZ executável".getBytes()))
                        .param("categoria", "OTHER").param("titulo", "Falso PDF").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(base + "/documentos")
                        .file(new MockMultipartFile("arquivo", "script.exe", "application/octet-stream", new byte[]{1, 2}))
                        .param("categoria", "OTHER").param("titulo", "Executável").header("Authorization", token))
                .andExpect(status().isBadRequest());

        byte[] baixado = mvc.perform(get(base + "/documentos/" + id + "/baixar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(baixado).isEqualTo(pdf);

        String outraAdministradora = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/documentos/" + id + "/baixar").header("Authorization", outraAdministradora)).andExpect(status().isNotFound());
        String chaveArmazenamento = jdbc.queryForObject("select storage_key from document where id = ?::uuid", String.class, id);
        assertThat(chaveArmazenamento).matches("[0-9a-f]{32}");
    }

    @Test
    void ocorrenciaCicloDeVidaComInteracoesEEstatisticas() throws Exception {
        JsonNode criada = call("POST", base + "/ocorrencias", Map.of("titulo", "Vazamento na garagem",
                "descricao", "Água escorrendo próximo à vaga 12", "categoria", "MAINTENANCE", "prioridade", "HIGH", "idUnidade", unidade101), 201);
        String id = criada.get("resumo").get("id").asText();
        assertThat(criada.get("resumo").get("numero").asInt()).isEqualTo(1);
        assertThat(call("POST", base + "/ocorrencias", Map.of("titulo", "Barulho", "descricao", "Festa até tarde",
                "categoria", "NOISE"), 201).get("resumo").get("numero").asInt()).isEqualTo(2);

        call("PUT", base + "/ocorrencias/" + id, Map.of("categoria", "MAINTENANCE", "prioridade", "URGENT",
                "nomeResponsavel", "Zelador João", "prazo", LocalDate.now().minusDays(1).toString()), 200);
        call("POST", base + "/ocorrencias/" + id + "/interacoes", Map.of("mensagem", "Fornecedor acionado", "interna", true), 200);
        call("POST", base + "/ocorrencias/" + id + "/situacao", Map.of("situacao", "RESOLVED"), 400);
        JsonNode estatisticas = call("GET", base + "/ocorrencias/estatisticas", null, 200);
        assertThat(estatisticas.get("atrasadas").asInt()).isEqualTo(1);

        JsonNode resolvida = call("POST", base + "/ocorrencias/" + id + "/situacao", Map.of("situacao", "RESOLVED",
                "mensagem", "Registro substituído"), 200);
        assertThat(resolvida.get("solucao").asText()).isEqualTo("Registro substituído");
        assertThat(resolvida.get("interacoes")).hasSize(2);
        call("PUT", base + "/ocorrencias/" + id, Map.of("categoria", "MAINTENANCE", "prioridade", "LOW"), 422);
    }

    @Test
    void comunicadoChegaAosMoradoresPorNotificacaoEEmail() throws Exception {
        String emailMorador = TestData.emailUnico();
        String pessoa = call("POST", "/api/v1/pessoas", Map.of("nome", "Rita Moradora", "tipo", "INDIVIDUAL", "email", emailMorador), 201)
                .get("id").asText();
        call("POST", base + "/ocorrencias", Map.of("titulo", "x", "descricao", "x", "categoria", "OTHER"), 201);
        call("POST", base + "/ocorrencias", Map.of("titulo", "y", "descricao", "y", "categoria", "OTHER"), 201);
        call("POST", base + "/vinculos", Map.of("idUnidade", unidade101, "idPessoa", pessoa, "tipo", "RESIDENT",
                "responsavelFinanceiro", false, "dataInicio", "2025-01-01"), 201);
        call("POST", "/api/v1/pessoas/" + pessoa + "/acesso-portal", null, 200);
        String usuarioMorador = jdbc.queryForObject("select id::text from app_user where person_id = ?::uuid", String.class, pessoa);

        call("POST", base + "/comunicados", Map.of("titulo", "Manutenção do elevador", "texto", "O elevador ficará parado amanhã.",
                "publicoAlvo", "UNIT", "idUnidade", unidade102, "enviarEmail", true), 201);
        assertThat(jdbc.queryForObject("select count(*) from notification where user_id = ?::uuid and title like 'Comunicado%'",
                Integer.class, usuarioMorador)).isZero();

        call("POST", base + "/comunicados", Map.of("titulo", "Assembleia em breve", "texto", "Participe!", "publicoAlvo", "ALL",
                "enviarEmail", true, "fixado", true), 201);
        assertThat(jdbc.queryForObject("select count(*) from notification where user_id = ?::uuid and title like 'Comunicado%'",
                Integer.class, usuarioMorador)).isEqualTo(1);
        assertThat(email.para(emailMorador).stream().anyMatch(m -> m.assunto().contains("Assembleia em breve"))).isTrue();

        JsonNode caixaEntrada = call("GET", "/api/v1/notificacoes", null, 200);
        assertThat(caixaEntrada.get("naoLidas").asInt()).isGreaterThanOrEqualTo(1);
        call("POST", "/api/v1/notificacoes/marcar-todas-lidas", null, 204);
        assertThat(call("GET", "/api/v1/notificacoes", null, 200).get("naoLidas").asInt()).isZero();
    }

    @Test
    void assembleiaDaConvocacaoAAtaComVotosPonderados() throws Exception {
        Instant quando = Instant.now().plus(10, ChronoUnit.DAYS);
        JsonNode criada = call("POST", base + "/assembleias", Map.of("titulo", "AGO 2026", "tipo", "ORDINARY",
                "primeiraConvocacaoEm", quando.toString(), "local", "Salão de festas", "criterioVoto", "IDEAL_FRACTION",
                "pauta", List.of(Map.of("titulo", "Aprovação de contas", "deliberativo", true),
                        Map.of("titulo", "Assuntos gerais", "deliberativo", false))), 201);
        String id = criada.get("id").asText();
        String item = criada.get("pauta").get(0).get("id").asText();

        call("POST", base + "/assembleias/" + id + "/iniciar", null, 422); // precisa estar convocada
        call("POST", base + "/assembleias/" + id + "/convocar", null, 200);
        assertThat(jdbc.queryForObject("select count(*) from announcement where condominium_id = ? and title like 'Convocação%'",
                Integer.class, condominio.getId())).isEqualTo(1);
        call("POST", base + "/assembleias/" + id + "/iniciar", null, 200);

        call("POST", base + "/assembleias/" + id + "/votos", Map.of("idItemPauta", item, "idUnidade", unidade101, "escolha", "YES"), 422);
        call("POST", base + "/assembleias/" + id + "/presencas", Map.of("idUnidade", unidade101, "nomePresente", "Ana", "porProcuracao", false), 200);
        call("POST", base + "/assembleias/" + id + "/presencas", Map.of("idUnidade", unidade102, "nomePresente", "Beto", "porProcuracao", true), 400);
        JsonNode comProcurador = call("POST", base + "/assembleias/" + id + "/presencas", Map.of("idUnidade", unidade102, "nomePresente", "Beto",
                "porProcuracao", true, "nomeProcurador", "Carlos Procurador"), 200);
        assertThat(comProcurador.get("quorum").get("fracaoPresente").decimalValue()).isEqualByComparingTo("100");

        call("POST", base + "/assembleias/" + id + "/votos", Map.of("idItemPauta", item, "idUnidade", unidade101, "escolha", "YES"), 200);
        call("POST", base + "/assembleias/" + id + "/votos", Map.of("idItemPauta", item, "idUnidade", unidade101, "escolha", "NO"), 409);
        JsonNode votado = call("POST", base + "/assembleias/" + id + "/votos", Map.of("idItemPauta", item, "idUnidade", unidade102, "escolha", "NO"), 200);
        JsonNode apuracao = votado.get("pauta").get(0).get("apuracao");
        assertThat(apuracao.get("sim").decimalValue()).isEqualByComparingTo("60");
        assertThat(apuracao.get("nao").decimalValue()).isEqualByComparingTo("40");

        call("PUT", base + "/assembleias/" + id + "/pauta/" + item + "/deliberacao", Map.of("deliberacao", "Contas aprovadas por maioria",
                "responsavel", "Síndico", "prazo", LocalDate.now().plusDays(30).toString()), 200);
        String rascunho = call("GET", base + "/assembleias/" + id + "/rascunho-ata", null, 200).get("ata").asText();
        assertThat(rascunho).contains("Contas aprovadas por maioria").contains("favoráveis 60").contains("2 de 2 unidades");
        call("PUT", base + "/assembleias/" + id + "/ata", Map.of("ata", rascunho), 200);
        assertThat(call("POST", base + "/assembleias/" + id + "/encerrar", null, 200).get("situacao").asText()).isEqualTo("CLOSED");
        call("POST", base + "/assembleias/" + id + "/votos", Map.of("idItemPauta", item, "idUnidade", unidade102, "escolha", "YES"), 422);
    }

    @Test
    void contratosHistoricoVencimentoERotinaDeLembretes() throws Exception {
        String fornecedor = call("POST", "/api/v1/fornecedores", Map.of("nome", "Elevadores Sobe Desce"), 201).get("id").asText();
        JsonNode criado = call("POST", base + "/contratos", Map.of("idFornecedor", fornecedor, "titulo", "Manutenção de elevadores",
                "valor", "1200.00", "periodicidade", "MONTHLY", "dataInicio", LocalDate.now().minusYears(1).toString(),
                "dataFim", LocalDate.now().plusDays(10).toString(), "indiceReajuste", "IPCA", "diasAvisoPrevio", 30), 201);
        assertThat(criado.get("aVencer").asBoolean()).isTrue();
        String id = criado.get("id").asText();
        JsonNode atualizado = call("PUT", base + "/contratos/" + id, Map.of("idFornecedor", fornecedor, "titulo", "Manutenção de elevadores",
                "valor", "1290.00", "periodicidade", "MONTHLY", "dataInicio", LocalDate.now().minusYears(1).toString(),
                "dataFim", LocalDate.now().plusDays(10).toString(), "versao", criado.get("versao").asLong()), 200);
        assertThat(atualizado.get("historico").get(0).get("alteracao").asText()).contains("1200.00 → 1290.00");

        LembretesDiariosJob rotina = new LembretesDiariosJob(contratoRepository, lancamentoRepository, comunicadoRepository,
                notificacaoService, Clock.systemUTC());
        tx.executeWithoutResult(s -> rotina.executar());
        tx.executeWithoutResult(s -> rotina.executar()); // não duplica
        assertThat(jdbc.queryForObject("select count(*) from notification where user_id = ? and title like 'Contrato a vencer%'",
                Integer.class, admin.getId())).isEqualTo(1);

        call("POST", base + "/contratos/" + id + "/encerrar", Map.of("dataEncerramento", LocalDate.now().toString(), "motivo", "Troca de fornecedor"), 200);
        assertThat(call("GET", base + "/contratos", null, 200).get(0).get("situacao").asText()).isEqualTo("TERMINATED");
    }
}
