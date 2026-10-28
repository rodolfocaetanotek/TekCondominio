package br.com.tekcondo.financeiro;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceiroIT extends IntegrationTest {

    static final String CONTA_MANUTENCAO = "20000000-0000-0000-0000-000000000205";
    static final String CONTA_TAXA_CONDOMINIAL = "20000000-0000-0000-0000-000000000101";

    @Autowired
    ObjectMapper objectMapper;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;
    String conta;
    LocalDate hoje = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Financeiro");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId() + "/financeiro";
        conta = chamar("POST", base + "/contas-bancarias", Map.of("nome", "Conta Corrente", "tipo", "CHECKING",
                "saldoInicial", "1000.00", "dataAbertura", hoje.minusMonths(6).toString()), 201).get("id").asText();
    }

    private JsonNode chamar(String metodo, String url, Object corpo, int esperado) throws Exception {
        var builder = switch (metodo) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", token);
        if (corpo != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(data.json(corpo));
        }
        String json = mvc.perform(builder).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private Map<String, Object> despesa(String valor, String idFornecedor, String documento) {
        Map<String, Object> m = new HashMap<>();
        m.put("natureza", "EXPENSE");
        m.put("descricao", "Manutenção do portão");
        m.put("idContaPlano", CONTA_MANUTENCAO);
        m.put("valor", valor);
        m.put("dataVencimento", hoje.plusDays(5).toString());
        m.put("idFornecedor", idFornecedor);
        m.put("numeroDocumento", documento);
        return m;
    }

    private Map<String, Object> baixa(String principal) {
        return Map.of("idContaBancaria", conta, "dataPagamento", hoje.toString(), "principal", principal, "formaPagamento", "PIX");
    }

    private String saldo() throws Exception {
        for (JsonNode c : chamar("GET", base + "/contas-bancarias", null, 200)) {
            if (c.get("id").asText().equals(conta)) {
                return c.get("saldo").decimalValue().setScale(2).toPlainString();
            }
        }
        throw new AssertionError("conta não encontrada");
    }

    @Test
    void contaAPagarExigeAprovacaoAceitaBaixasParciaisEEstorno() throws Exception {
        String fornecedor = chamar("POST", "/api/v1/suppliers", Map.of("name", "Portões Ltda", "document", "11.222.333/0001-81"), 201)
                .get("id").asText();
        JsonNode criado = chamar("POST", base + "/lancamentos", despesa("500.00", fornecedor, "NF-123"), 201).get(0);
        String id = criado.get("resumo").get("id").asText();
        assertThat(criado.get("resumo").get("aprovacao").asText()).isEqualTo("PENDING");

        chamar("POST", base + "/lancamentos", despesa("500.00", fornecedor, "NF-123"), 409); // mesma nota do mesmo fornecedor
        chamar("POST", base + "/lancamentos/" + id + "/baixas", baixa("100.00"), 422); // sem aprovação
        chamar("POST", base + "/lancamentos/" + id + "/aprovar", Map.of("observacao", "Ok"), 200);

        JsonNode parcial = chamar("POST", base + "/lancamentos/" + id + "/baixas", baixa("300.00"), 200);
        assertThat(parcial.get("resumo").get("situacao").asText()).isEqualTo("PARTIAL");
        assertThat(parcial.get("resumo").get("valorAberto").decimalValue()).isEqualByComparingTo("200.00");
        chamar("POST", base + "/lancamentos/" + id + "/baixas", baixa("800.00"), 422);
        JsonNode pago = chamar("POST", base + "/lancamentos/" + id + "/baixas", baixa("200.00"), 200);
        assertThat(pago.get("resumo").get("situacao").asText()).isEqualTo("PAID");
        assertThat(saldo()).isEqualTo("500.00");

        chamar("POST", base + "/lancamentos/" + id + "/cancelar", Map.of("motivo", "Lançado errado"), 422); // possui baixas
        String idBaixa = pago.get("baixas").get(1).get("id").asText();
        chamar("POST", base + "/baixas/" + idBaixa + "/estornar", Map.of("motivo", "x"), 400);
        JsonNode estornado = chamar("POST", base + "/baixas/" + idBaixa + "/estornar", Map.of("motivo", "Pagamento devolvido pelo banco"), 200);
        assertThat(estornado.get("resumo").get("situacao").asText()).isEqualTo("PARTIAL");
        assertThat(estornado.get("baixas").get(1).get("estornada").asBoolean()).isTrue();
        assertThat(saldo()).isEqualTo("700.00");
    }

    @Test
    void calculoDeEncargosUsaPercentuaisFotografadosNoTitulo() throws Exception {
        String unidade = chamar("POST", "/api/v1/condominios/" + condominio.getId() + "/unidades",
                Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "10"), 201).get("id").asText();
        Map<String, Object> receita = new HashMap<>();
        receita.put("natureza", "INCOME");
        receita.put("descricao", "Taxa condominial");
        receita.put("idContaPlano", CONTA_TAXA_CONDOMINIAL);
        receita.put("valor", "500.00");
        receita.put("dataVencimento", hoje.minusDays(30).toString());
        receita.put("idUnidade", unidade);
        String id = chamar("POST", base + "/lancamentos", receita, 201).get(0).get("resumo").get("id").asText();

        // Alterar o parâmetro depois não muda o título já emitido.
        chamar("PUT", base + "/parametros", Map.of("percentualMulta", "1.00", "percentualJurosMensal", "1.00",
                "diaVencimento", 10, "exigeAprovacaoContasPagar", true), 200);
        chamar("PUT", base + "/parametros", Map.of("percentualMulta", "5.00", "percentualJurosMensal", "1.00",
                "diaVencimento", 10, "exigeAprovacaoContasPagar", true), 400);

        JsonNode encargos = chamar("GET", base + "/lancamentos/" + id + "/encargos?data=" + hoje, null, 200);
        assertThat(encargos.get("diasAtraso").asInt()).isEqualTo(30);
        assertThat(encargos.get("multa").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(encargos.get("juros").decimalValue()).isEqualByComparingTo("5.00");

        Map<String, Object> pagamento = new HashMap<>(baixa("500.00"));
        pagamento.put("multa", "10.00");
        pagamento.put("juros", "5.00");
        JsonNode pago = chamar("POST", base + "/lancamentos/" + id + "/baixas", pagamento, 200);
        assertThat(pago.get("baixas").get(0).get("total").decimalValue()).isEqualByComparingTo("515.00");
        assertThat(saldo()).isEqualTo("1515.00");

        mvc.perform(get(base + "/lancamentos").header("Authorization", token).param("vencido", "true"))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void recorrenciaGeraParcelasMensaisEValidaNaturezaDaConta() throws Exception {
        Map<String, Object> corpo = despesa("150.00", null, null);
        corpo.put("repeticoes", 3);
        JsonNode lista = chamar("POST", base + "/lancamentos", corpo, 201);
        assertThat(lista).hasSize(3);
        assertThat(lista.get(2).get("resumo").get("rotuloParcela").asText()).isEqualTo("3/3");
        assertThat(lista.get(2).get("resumo").get("dataVencimento").asText()).isEqualTo(hoje.plusDays(5).plusMonths(2).toString());

        Map<String, Object> errada = despesa("10.00", null, null);
        errada.put("idContaPlano", CONTA_TAXA_CONDOMINIAL);
        chamar("POST", base + "/lancamentos", errada, 422);
        Map<String, Object> grupo = despesa("10.00", null, null);
        grupo.put("idContaPlano", "20000000-0000-0000-0000-000000000002");
        chamar("POST", base + "/lancamentos", grupo, 422);
    }

    @Test
    void transferenciasExtratoEConciliacao() throws Exception {
        String caixa = chamar("POST", base + "/contas-bancarias", Map.of("nome", "Caixa", "tipo", "CASH",
                "dataAbertura", hoje.minusMonths(1).toString()), 201).get("id").asText();
        chamar("POST", base + "/transferencias", Map.of("idContaOrigem", caixa, "idContaDestino", conta, "valor", "50.00",
                "data", hoje.toString()), 422); // caixa sem saldo
        String transferencia = chamar("POST", base + "/transferencias", Map.of("idContaOrigem", conta, "idContaDestino", caixa,
                "valor", "200.00", "data", hoje.toString(), "descricao", "Suprimento de caixa"), 201).get("id").asText();

        JsonNode extrato = chamar("GET", base + "/contas-bancarias/" + conta + "/extrato?de=" + hoje.withDayOfMonth(1), null, 200);
        assertThat(extrato.get("saldoInicial").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(extrato.get("saldoFinal").decimalValue()).isEqualByComparingTo("800.00");
        assertThat(extrato.get("movimentacoes")).hasSize(1);

        chamar("POST", base + "/conciliar", Map.of("itens", List.of(Map.of("tipo", "TRANSFER_OUT", "id", transferencia)),
                "conciliada", true), 200);
        chamar("POST", base + "/transferencias/" + transferencia + "/estornar", Map.of("motivo", "Lançada em duplicidade"), 422);
        chamar("POST", base + "/conciliar", Map.of("itens", List.of(Map.of("tipo", "TRANSFER_OUT", "id", transferencia)),
                "conciliada", false), 200);
        chamar("POST", base + "/transferencias/" + transferencia + "/estornar", Map.of("motivo", "Lançada em duplicidade"), 200);
        assertThat(saldo()).isIn("1000.00");
    }

    @Test
    void orcamentoComparaPrevistoComRealizado() throws Exception {
        JsonNode lancamento = chamar("POST", base + "/lancamentos", despesa("120.00", null, null), 201).get(0);
        String id = lancamento.get("resumo").get("id").asText();
        chamar("POST", base + "/lancamentos/" + id + "/aprovar", null, 200);
        chamar("POST", base + "/lancamentos/" + id + "/baixas", baixa("120.00"), 200);

        int ano = hoje.getYear();
        JsonNode orcamento = chamar("PUT", base + "/orcamento/" + ano, Map.of("celulas", List.of(
                Map.of("idContaPlano", CONTA_MANUTENCAO, "mes", hoje.getMonthValue(), "valor", "300.00"))), 200);
        JsonNode linha = null;
        for (JsonNode c : orcamento.get("contas")) {
            if (c.get("idContaPlano").asText().equals(CONTA_MANUTENCAO)) linha = c;
        }
        assertThat(linha).isNotNull();
        assertThat(linha.get("totalPrevisto").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(linha.get("totalRealizado").decimalValue()).isEqualByComparingTo("120.00");
    }

    @Test
    void papeisEIsolamento() throws Exception {
        JsonNode lancamento = chamar("POST", base + "/lancamentos", despesa("90.00", null, null), 201).get(0);
        String id = lancamento.get("resumo").get("id").asText();

        Usuario sindico = data.usuario(administradora);
        data.conceder(sindico, "SYNDIC", condominio.getId());
        String tokenSindico = TestData.bearer(data.login(sindico));
        mvc.perform(post(base + "/lancamentos/" + id + "/aprovar").header("Authorization", tokenSindico))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/lancamentos").header("Authorization", tokenSindico).contentType(MediaType.APPLICATION_JSON)
                .content(data.json(despesa("1.00", null, null)))).andExpect(status().isForbidden());
        mvc.perform(get(base + "/lancamentos").header("Authorization", tokenSindico))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conteudo", hasSize(1)));

        String outra = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/lancamentos/" + id).header("Authorization", outra)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/contas-bancarias").header("Authorization", outra)).andExpect(status().isNotFound());
    }
}
