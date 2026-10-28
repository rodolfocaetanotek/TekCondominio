package br.com.tekcondo.financeiro;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import br.com.tekcondo.condominio.entity.Condominio;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FechamentoPeriodoIT extends IntegrationTest {

    static final String CONTA_TAXA_CONDOMINIAL = "20000000-0000-0000-0000-000000000101";

    @Autowired
    ObjectMapper objectMapper;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;
    String conta;
    String caixa;
    LocalDate hoje = LocalDate.now();
    LocalDate fimMesPassado = hoje.withDayOfMonth(1).minusDays(1);
    LocalDate diaMesPassado = fimMesPassado.withDayOfMonth(10);

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Fechamento");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId() + "/financeiro";
        conta = chamar("POST", base + "/contas-bancarias", Map.of("nome", "Conta Corrente", "tipo", "CHECKING",
                "saldoInicial", "1000.00", "dataAbertura", hoje.minusMonths(6).toString()), 201).get("id").asText();
        caixa = chamar("POST", base + "/contas-bancarias", Map.of("nome", "Caixa", "tipo", "CASH",
                "saldoInicial", "0", "dataAbertura", hoje.minusMonths(6).toString()), 201).get("id").asText();
    }

    private JsonNode chamar(String metodo, String url, Object corpo, int esperado) throws Exception {
        return chamar(token, metodo, url, corpo, esperado);
    }

    private JsonNode chamar(String auth, String metodo, String url, Object corpo, int esperado) throws Exception {
        var builder = switch (metodo) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", auth);
        if (corpo != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(data.json(corpo));
        }
        String json = mvc.perform(builder).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private Map<String, Object> receita(String valor, LocalDate vencimento) {
        Map<String, Object> m = new HashMap<>();
        m.put("natureza", "INCOME");
        m.put("descricao", "Receita avulsa");
        m.put("idContaPlano", CONTA_TAXA_CONDOMINIAL);
        m.put("valor", valor);
        m.put("dataVencimento", vencimento.toString());
        return m;
    }

    private Map<String, Object> baixa(LocalDate dataPagamento, String principal) {
        return Map.of("idContaBancaria", conta, "dataPagamento", dataPagamento.toString(), "principal", principal,
                "formaPagamento", "PIX");
    }

    @Test
    void fechamentoCongelaOPeriodoAteReaberturaComJustificativa() throws Exception {
        String primeiro = chamar("POST", base + "/lancamentos", receita("300.00", diaMesPassado), 201).get(0).get("resumo").get("id").asText();
        JsonNode pago = chamar("POST", base + "/lancamentos/" + primeiro + "/baixas", baixa(diaMesPassado, "300.00"), 200);
        String idBaixa = pago.get("baixas").get(0).get("id").asText();
        String segundo = chamar("POST", base + "/lancamentos", receita("200.00", diaMesPassado), 201).get(0).get("resumo").get("id").asText();

        JsonNode situacao = chamar("GET", base + "/fechamento", null, 200);
        assertThat(situacao.get("sugestaoAte").asText()).isEqualTo(fimMesPassado.toString());
        assertThat(situacao.has("fechadoAte")).isFalse();

        JsonNode verificacao = chamar("GET", base + "/fechamento/verificar?ate=" + fimMesPassado, null, 200);
        assertThat(verificacao.get("movimentacoesNaoConciliadas").asLong()).isEqualTo(1);
        assertThat(verificacao.get("receitas").decimalValue()).isEqualByComparingTo("300.00");

        // Somente fim de mês e somente meses encerrados.
        chamar("POST", base + "/fechamento", Map.of("ate", fimMesPassado.minusDays(3).toString()), 400);
        LocalDate fimEsteMes = hoje.withDayOfMonth(hoje.lengthOfMonth());
        chamar("POST", base + "/fechamento", Map.of("ate", fimEsteMes.toString()), 422);

        // Funcionário da administradora não fecha período.
        Usuario funcionario = data.usuario(administradora);
        data.conceder(funcionario, "ORG_STAFF", null);
        chamar(TestData.bearer(data.login(funcionario)), "POST", base + "/fechamento", Map.of("ate", fimMesPassado.toString()), 403);

        JsonNode fechado = chamar("POST", base + "/fechamento", Map.of("ate", fimMesPassado.toString(), "observacoes", "Conferido"), 200);
        assertThat(fechado.get("fechadoAte").asText()).isEqualTo(fimMesPassado.toString());
        JsonNode fotografia = fechado.get("historico").get(0).get("fotografia");
        assertThat(fotografia.get("income").asText()).isEqualTo("300.00");
        assertThat(fotografia.get("balances")).hasSize(2);
        chamar("POST", base + "/fechamento", Map.of("ate", fimMesPassado.toString()), 409);

        // Nada retroativo: baixa, estorno, lançamento, transferência e conciliação no período fechado.
        chamar("POST", base + "/lancamentos/" + segundo + "/baixas", baixa(diaMesPassado, "200.00"), 422);
        chamar("POST", base + "/baixas/" + idBaixa + "/estornar", Map.of("motivo", "Devolução bancária"), 422);
        chamar("POST", base + "/lancamentos", receita("50.00", diaMesPassado), 422);
        chamar("POST", base + "/lancamentos/" + segundo + "/cancelar", Map.of("motivo", "Lançado em duplicidade"), 422);
        chamar("POST", base + "/transferencias", Map.of("idContaOrigem", conta, "idContaDestino", caixa, "valor", "10.00",
                "data", diaMesPassado.toString()), 422);
        chamar("POST", base + "/conciliar", Map.of("conciliada", true,
                "itens", java.util.List.of(Map.of("tipo", "PAYMENT", "id", idBaixa))), 422);

        // O período aberto continua operando normalmente.
        chamar("POST", base + "/lancamentos/" + segundo + "/baixas", baixa(hoje, "200.00"), 200);
        chamar("POST", base + "/lancamentos", receita("80.00", hoje), 201);

        chamar("POST", base + "/fechamento/reabrir", Map.of("motivo", "x"), 400);
        JsonNode reaberto = chamar("POST", base + "/fechamento/reabrir", Map.of("motivo", "Estorno solicitado pelo banco"), 200);
        assertThat(reaberto.has("fechadoAte")).isFalse();
        assertThat(reaberto.get("historico")).hasSize(2);
        assertThat(reaberto.get("historico").get(0).get("acao").asText()).isEqualTo("REOPEN");
        chamar("POST", base + "/baixas/" + idBaixa + "/estornar", Map.of("motivo", "Devolução bancária"), 200);
    }

    @Test
    void fechamentoEIsoladoPorAdministradora() throws Exception {
        String outra = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        chamar(outra, "GET", base + "/fechamento", null, 404);
        chamar(outra, "POST", base + "/fechamento", Map.of("ate", fimMesPassado.toString()), 404);
    }
}
