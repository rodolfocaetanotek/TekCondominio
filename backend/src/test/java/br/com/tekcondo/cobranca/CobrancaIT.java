package br.com.tekcondo.cobranca;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CobrancaIT extends IntegrationTest {

    static final String CONTA_TAXA_CONDOMINIAL = "20000000-0000-0000-0000-000000000101";
    static final String CONTA_FUNDO_RESERVA = "20000000-0000-0000-0000-000000000102";

    @Autowired
    ObjectMapper objectMapper;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;
    String unidadeA;
    LocalDate hoje = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Cobrança");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId();
        unidadeA = chamar("POST", base + "/unidades", Map.of("identificacao", "A", "tipo", "APARTMENT", "fracaoIdeal", "50"), 201).get("id").asText();
        chamar("POST", base + "/unidades", Map.of("identificacao", "B", "tipo", "APARTMENT", "fracaoIdeal", "30"), 201);
        chamar("POST", base + "/unidades", Map.of("identificacao", "C", "tipo", "APARTMENT", "fracaoIdeal", "20"), 201);
        String pessoa = chamar("POST", "/api/v1/pessoas", Map.of("nome", "Ana Pagadora", "tipo", "INDIVIDUAL"), 201).get("id").asText();
        chamar("POST", base + "/vinculos", Map.of("idUnidade", unidadeA, "idPessoa", pessoa, "tipo", "OWNER",
                "responsavelFinanceiro", true, "dataInicio", "2024-01-01"), 201);
    }

    private JsonNode chamar(String metodo, String url, Object corpo, int esperado) throws Exception {
        var builder = "POST".equals(metodo) ? post(url) : get(url);
        builder.header("Authorization", token);
        if (corpo != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(data.json(corpo));
        }
        String json = mvc.perform(builder).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return json.isEmpty() ? null : objectMapper.readTree(json);
    }

    private Map<String, Object> rateio(YearMonth competencia, String conta, LocalDate vencimento) {
        Map<String, Object> m = new HashMap<>();
        m.put("competencia", competencia.toString());
        m.put("descricao", "Taxa condominial " + competencia);
        m.put("idContaPlano", conta);
        m.put("criterio", "IDEAL_FRACTION");
        m.put("valor", "1000.00");
        m.put("dataVencimento", vencimento == null ? null : vencimento.toString());
        return m;
    }

    @Test
    void simulaGeraRecusaDuplicidadeECancelaRateio() throws Exception {
        YearMonth proxima = YearMonth.from(hoje).plusMonths(1);
        JsonNode simulacao = chamar("POST", base + "/cobranca/rateios/simular", rateio(proxima, CONTA_TAXA_CONDOMINIAL, null), 200);
        assertThat(simulacao.get("total").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(simulacao.get("unidadesSemResponsavel").asInt()).isEqualTo(2);
        assertThat(simulacao.get("dataVencimento").asText()).isEqualTo(proxima.atDay(10).toString());
        assertThat(simulacao.get("linhas").get(0).get("valor").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(simulacao.get("linhas").get(0).get("nomeResponsavel").asText()).isEqualTo("Ana Pagadora");

        String id = chamar("POST", base + "/cobranca/rateios", rateio(proxima, CONTA_TAXA_CONDOMINIAL, null), 201).get("id").asText();
        chamar("POST", base + "/cobranca/rateios", rateio(proxima, CONTA_TAXA_CONDOMINIAL, null), 409);
        chamar("POST", base + "/cobranca/rateios", rateio(proxima, CONTA_FUNDO_RESERVA, null), 201); // outra conta, mesma competência

        JsonNode cobrancas = chamar("GET", base + "/financeiro/lancamentos?natureza=INCOME&idUnidade=" + unidadeA, null, 200);
        assertThat(cobrancas.get("totalElementos").asInt()).isEqualTo(2);
        assertThat(cobrancas.get("conteudo").get(0).get("nomePessoa").asText()).isEqualTo("Ana Pagadora");

        chamar("POST", base + "/cobranca/rateios/" + id + "/cancelar", Map.of("motivo", "Valor aprovado em assembleia mudou"), 200);
        chamar("POST", base + "/cobranca/rateios", rateio(proxima, CONTA_TAXA_CONDOMINIAL, null), 201);
    }

    @Test
    void fracaoIdealExigeUnidadesComFracoesFechadas() throws Exception {
        chamar("POST", base + "/unidades", Map.of("identificacao", "D", "tipo", "STORE", "fracaoIdeal", "5"), 201);
        chamar("POST", base + "/cobranca/rateios/simular", rateio(YearMonth.from(hoje), CONTA_TAXA_CONDOMINIAL, null), 422);
        Map<String, Object> igualitario = rateio(YearMonth.from(hoje), CONTA_TAXA_CONDOMINIAL, null);
        igualitario.put("criterio", "EQUAL");
        JsonNode simulacao = chamar("POST", base + "/cobranca/rateios/simular", igualitario, 200);
        assertThat(simulacao.get("linhas").get(0).get("valor").decimalValue()).isEqualByComparingTo("250.00");
    }

    @Test
    void inadimplenciaDemonstrativoAcordoECancelamento() throws Exception {
        YearMonth passada = YearMonth.from(hoje).minusMonths(2);
        chamar("POST", base + "/cobranca/rateios", rateio(passada, CONTA_TAXA_CONDOMINIAL, hoje.minusDays(60)), 201);

        JsonNode relatorio = chamar("GET", base + "/cobranca/inadimplencia", null, 200);
        assertThat(relatorio.get("unidadesEmDebito").asInt()).isEqualTo(3);
        assertThat(relatorio.get("totalPrincipal").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(relatorio.get("totalEncargos").decimalValue()).isEqualByComparingTo("40.00"); // multa 2% (20) + juros 2 meses (20)
        assertThat(relatorio.get("indiceInadimplencia").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(relatorio.get("linhas").get(0).get("rotuloUnidade").asText()).isEqualTo("A");

        chamar("POST", base + "/cobranca/unidades/" + unidadeA + "/notas", Map.of("tipoContato", "PHONE",
                "dataContato", hoje.toString(), "observacao", "Prometeu pagar na sexta"), 201);
        JsonNode debitos = chamar("GET", base + "/cobranca/unidades/" + unidadeA + "/debitos", null, 200);
        assertThat(debitos.get("totalAberto").decimalValue()).isEqualByComparingTo("520.00");
        assertThat(debitos.get("notas").get(0).get("observacao").asText()).isEqualTo("Prometeu pagar na sexta");
        String cobranca = debitos.get("linhas").get(0).get("idLancamento").asText();

        Map<String, Object> acordo = new HashMap<>();
        acordo.put("idUnidade", unidadeA);
        acordo.put("idsLancamentos", List.of(cobranca));
        acordo.put("totalParcelas", 3);
        acordo.put("dataPrimeiroVencimento", hoje.plusDays(10).toString());
        acordo.put("desconto", "10.00");
        acordo.put("incluirEncargos", true);
        JsonNode criado = chamar("POST", base + "/cobranca/acordos", acordo, 201);
        assertThat(criado.get("valorTotal").decimalValue()).isEqualByComparingTo("510.00");
        BigDecimal soma = BigDecimal.ZERO;
        for (JsonNode parcela : criado.get("parcelas")) {
            soma = soma.add(parcela.get("valor").decimalValue());
        }
        assertThat(soma).isEqualByComparingTo("510.00");
        chamar("POST", base + "/cobranca/acordos", acordo, 422); // débito já renegociado

        relatorio = chamar("GET", base + "/cobranca/inadimplencia", null, 200);
        assertThat(relatorio.get("unidadesEmDebito").asInt()).isEqualTo(2);

        chamar("POST", base + "/cobranca/acordos/" + criado.get("id").asText() + "/cancelar",
                Map.of("motivo", "Condômino desistiu do acordo"), 200);
        relatorio = chamar("GET", base + "/cobranca/inadimplencia", null, 200);
        assertThat(relatorio.get("unidadesEmDebito").asInt()).isEqualTo(3);
    }

    @Test
    void conselhoConsultaMasNaoGeraCobrancas() throws Exception {
        Usuario conselheiro = data.usuario(administradora);
        data.conceder(conselheiro, "COUNCIL", condominio.getId());
        String tokenConselho = TestData.bearer(data.login(conselheiro));
        mvc.perform(get(base + "/cobranca/inadimplencia").header("Authorization", tokenConselho)).andExpect(status().isOk());
        mvc.perform(post(base + "/cobranca/rateios").header("Authorization", tokenConselho)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data.json(rateio(YearMonth.from(hoje), CONTA_TAXA_CONDOMINIAL, null))))
                .andExpect(status().isForbidden());
    }
}
