package br.com.tekcondo.indicador;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndicadorIT extends IntegrationTest {

    static final String CONTA_TAXA = "20000000-0000-0000-0000-000000000101";
    static final String CONTA_AGUA = "20000000-0000-0000-0000-000000000202";

    @Autowired
    ObjectMapper objectMapper;

    Condominio condominio;
    String token;
    String base;
    String conta;
    LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void setUp() throws Exception {
        var administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Indicadores");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId();
        conta = chamar("POST", base + "/financeiro/contas-bancarias", Map.of("nome", "Conta", "tipo", "CHECKING",
                "saldoInicial", "1000.00", "dataAbertura", hoje.minusYears(1).toString()), 201).get("id").asText();
    }

    private JsonNode chamar(String metodo, String url, Object corpo, int esperado) throws Exception {
        var builder = "POST".equals(metodo) ? post(url) : get(url);
        builder.header("Authorization", token);
        if (corpo != null) builder.contentType(MediaType.APPLICATION_JSON).content(data.json(corpo));
        String json = mvc.perform(builder).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private String lancamento(String natureza, String idContaPlano, String valor, LocalDate vencimento) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("natureza", natureza);
        m.put("descricao", natureza + " " + vencimento);
        m.put("idContaPlano", idContaPlano);
        m.put("valor", valor);
        m.put("dataVencimento", vencimento.toString());
        return chamar("POST", base + "/financeiro/lancamentos", m, 201).get(0).get("resumo").get("id").asText();
    }

    private void despesaPaga(String valor, LocalDate em) throws Exception {
        String id = lancamento("EXPENSE", CONTA_AGUA, valor, em);
        chamar("POST", base + "/financeiro/lancamentos/" + id + "/aprovar", Map.of(), 200);
        chamar("POST", base + "/financeiro/lancamentos/" + id + "/baixas", Map.of("idContaBancaria", conta,
                "dataPagamento", em.toString(), "principal", valor, "formaPagamento", "PIX"), 200);
    }

    @Test
    void projecaoUsaVencimentosEmAbertoESeparaVencidos() throws Exception {
        lancamento("INCOME", CONTA_TAXA, "500.00", hoje.plusDays(2));
        lancamento("EXPENSE", CONTA_AGUA, "2000.00", hoje.plusDays(9));
        lancamento("INCOME", CONTA_TAXA, "300.00", hoje.minusDays(15));

        JsonNode p = chamar("GET", base + "/indicadores/projecao-caixa?semanas=4", null, 200);
        assertThat(p.get("saldoAtual").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(p.get("aReceberVencido").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(p.get("semanas")).hasSize(4);
        assertThat(p.get("semanas").get(0).get("saldoProjetado").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(p.get("menorSaldoProjetado").decimalValue()).isEqualByComparingTo("-500.00");
        assertThat(p.get("observacoes").toString()).contains("negativo");
    }

    @Test
    void apontaDespesaMuitoAcimaDoHistorico() throws Exception {
        LocalDate mesPassado = hoje.minusMonths(1).withDayOfMonth(5);
        for (int i = 1; i <= 6; i++) {
            despesaPaga(i % 2 == 0 ? "1000.00" : "1040.00", mesPassado.minusMonths(i));
        }
        despesaPaga("3100.00", mesPassado);

        JsonNode despesas = chamar("GET", base + "/indicadores/despesas-fora-do-padrao", null, 200);
        assertThat(despesas).hasSize(1);
        assertThat(despesas.get(0).get("codigoConta").asText()).isEqualTo("2.02");
        assertThat(despesas.get(0).get("mediaHistorica").decimalValue()).isEqualByComparingTo("1020.00");
        assertThat(despesas.get(0).get("mes").asText()).isEqualTo(mesPassado.toString().substring(0, 7));
    }
}
