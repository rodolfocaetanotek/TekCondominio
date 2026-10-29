package br.com.tekcondo.reserva;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.GravacaoEmailGateway;
import br.com.tekcondo.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservaIT extends IntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    GravacaoEmailGateway email;
    @Autowired
    JdbcTemplate jdbc;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;
    String portal;
    String unidade101;
    String unidade102;
    String morador;
    LocalDate dia = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(10);

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Reservas");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId();
        portal = "/api/v1/portal/" + condominio.getId();
        unidade101 = call(token, "POST", base + "/unidades", Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        unidade102 = call(token, "POST", base + "/unidades", Map.of("identificacao", "102", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        String emailMorador = TestData.emailUnico();
        String pessoa = call(token, "POST", "/api/v1/pessoas", Map.of("nome", "Rita Proprietária", "tipo", "INDIVIDUAL", "email", emailMorador), 201)
                .get("id").asText();
        call(token, "POST", base + "/vinculos", Map.of("idUnidade", unidade101, "idPessoa", pessoa, "tipo", "OWNER",
                "responsavelFinanceiro", true, "dataInicio", "2024-01-01"), 201);
        call(token, "POST", "/api/v1/pessoas/" + pessoa + "/acesso-portal", null, 200);
        morador = ativar(emailMorador);
    }

    private JsonNode call(String auth, String method, String url, Object body, int expected) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
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

    private String area(String nome, String taxa, boolean exigeAprovacao, boolean bloqueiaInadimplente, int antecedenciaMinimaHoras,
                        Integer limiteMensal) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("nome", nome);
        m.put("taxa", taxa);
        m.put("exigeAprovacao", exigeAprovacao);
        m.put("bloqueiaInadimplente", bloqueiaInadimplente);
        m.put("horaAbertura", "10:00");
        m.put("horaFechamento", "23:00");
        m.put("duracaoMaximaMinutos", 600);
        m.put("antecedenciaMinimaHoras", antecedenciaMinimaHoras);
        m.put("antecedenciaMaximaDias", 90);
        m.put("limiteMensalPorUnidade", limiteMensal);
        return call(token, "POST", base + "/areas-comuns", m, 201).get("id").asText();
    }

    private Map<String, Object> reserva(String area, String unidade, LocalDate data, String inicio, String fim) {
        return Map.of("idArea", area, "idUnidade", unidade, "data", data.toString(), "horaInicio", inicio, "horaFim", fim, "convidados", 40);
    }

    @Test
    void moradorReservaComAprovacaoTaxaLimitesEProtecaoContraConflito() throws Exception {
        String salao = area("Salão de festas", "150.00", true, true, 24, 1);

        call(morador, "POST", portal + "/reservas", reserva(salao, unidade102, dia, "18:00", "22:00"), 403); // unidade de outro
        call(morador, "POST", portal + "/reservas", reserva(salao, unidade101, dia, "09:00", "12:00"), 422); // fora do horário
        JsonNode pendente = call(morador, "POST", portal + "/reservas", reserva(salao, unidade101, dia, "18:00", "22:00"), 201);
        assertThat(pendente.get("situacao").asText()).isEqualTo("PENDING");
        assertThat(pendente.has("idLancamento")).isFalse();
        call(morador, "POST", portal + "/reservas", reserva(salao, unidade101, dia.plusDays(1), "12:00", "14:00"), 422); // limite mensal
        call(token, "POST", base + "/reservas", reserva(salao, unidade102, dia, "20:00", "23:00"), 409); // sobreposição

        JsonNode horarios = call(morador, "GET", portal + "/areas-comuns/" + salao + "/ocupacao?de=" + dia + "&ate=" + dia, null, 200);
        assertThat(horarios).hasSize(1);
        assertThat(horarios.get(0).has("idUnidade")).isFalse(); // não expõe a unidade

        String id = pendente.get("id").asText();
        call(token, "POST", base + "/reservas/" + id + "/recusar", Map.of(), 400); // recusa exige motivo
        JsonNode confirmada = call(token, "POST", base + "/reservas/" + id + "/aprovar", Map.of("observacao", "Ok"), 200);
        assertThat(confirmada.get("situacao").asText()).isEqualTo("CONFIRMED");
        String lancamento = confirmada.get("idLancamento").asText();
        JsonNode cobranca = call(token, "GET", base + "/financeiro/lancamentos/" + lancamento, null, 200).get("resumo");
        assertThat(cobranca.get("valor").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(cobranca.get("rotuloUnidade").asText()).isEqualTo("101");
        assertThat(cobranca.get("nomePessoa").asText()).isEqualTo("Rita Proprietária");

        JsonNode cancelada = call(morador, "POST", portal + "/reservas/" + id + "/cancelar", Map.of("motivo", "Mudança de planos"), 200);
        assertThat(cancelada.get("situacao").asText()).isEqualTo("CANCELLED");
        assertThat(call(token, "GET", base + "/financeiro/lancamentos/" + lancamento, null, 200).get("resumo").get("situacao").asText()).isEqualTo("CANCELLED");

        // Inadimplente não reserva área com bloqueio.
        Map<String, Object> vencido = new HashMap<>();
        vencido.put("natureza", "INCOME");
        vencido.put("descricao", "Taxa condominial");
        vencido.put("idContaPlano", "20000000-0000-0000-0000-000000000101");
        vencido.put("valor", "400.00");
        vencido.put("dataVencimento", LocalDate.now().minusDays(20).toString());
        vencido.put("idUnidade", unidade101);
        call(token, "POST", base + "/financeiro/lancamentos", vencido, 201);
        call(morador, "POST", portal + "/reservas", reserva(salao, unidade101, dia, "18:00", "22:00"), 422);

        assertThat(call(morador, "GET", portal + "/reservas", null, 200)).hasSize(1);
    }

    @Test
    void antecedenciaValeParaMoradoresEBancoRecusaSobreposicao() throws Exception {
        String churrasqueira = area("Churrasqueira", "0", false, false, 48, null);
        LocalDate amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
        call(morador, "POST", portal + "/reservas", reserva(churrasqueira, unidade101, amanha, "12:00", "16:00"), 422);
        // A administração pode lançar com menos antecedência.
        call(token, "POST", base + "/reservas", reserva(churrasqueira, unidade102, amanha, "12:00", "16:00"), 201);
        JsonNode ok = call(morador, "POST", portal + "/reservas", reserva(churrasqueira, unidade101, dia, "12:00", "16:00"), 201);
        assertThat(ok.get("situacao").asText()).isEqualTo("CONFIRMED");

        // Segunda barreira: mesmo burlando o serviço, o banco não aceita horário sobreposto.
        assertThatThrownBy(() -> jdbc.update("""
                insert into reservation (id, organization_id, condominium_id, area_id, unit_id, reservation_date, start_minute, end_minute,
                  status, requester_name, by_resident, created_at, updated_at)
                values (?, ?, ?, ?::uuid, ?::uuid, ?, 900, 1080, 'CONFIRMED', 'x', false, now(), now())
                """, UUID.randomUUID(), administradora.getId(), condominio.getId(), churrasqueira, unidade102, dia))
                .isInstanceOf(DataIntegrityViolationException.class);

        String outraAdministradora = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        call(outraAdministradora, "GET", base + "/areas-comuns", null, 404);
        call(morador, "POST", base + "/areas-comuns", Map.of("nome", "x", "horaAbertura", "08:00", "horaFechamento", "09:00"), 403);
    }
}
