package br.com.tekcondo.financeiro;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportacaoExtratoIT extends IntegrationTest {

    static final String CONTA_TAXA_CONDOMINIAL = "20000000-0000-0000-0000-000000000101";
    static final String CONTA_TARIFAS_BANCARIAS = "20000000-0000-0000-0000-000000000211";

    @Autowired
    ObjectMapper objectMapper;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;
    String conta;
    LocalDate d1 = LocalDate.now().minusDays(5);

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Extrato");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId() + "/financeiro";
        conta = chamar("POST", base + "/contas-bancarias", Map.of("nome", "Sicoob", "tipo", "CHECKING", "codigoBanco", "756",
                "agencia", "4321", "numeroConta", "12345-6", "saldoInicial", "1000.00",
                "dataAbertura", d1.minusMonths(1).toString()), 201).get("id").asText();
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

    private JsonNode enviar(String ofx, int esperado) throws Exception {
        String json = mvc.perform(multipart(base + "/contas-bancarias/" + conta + "/extratos")
                        .file(new MockMultipartFile("arquivo", "extrato.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.ISO_8859_1)))
                        .header("Authorization", token))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String baixaRecebida(String valor, LocalDate dataPagamento) throws Exception {
        Map<String, Object> receita = new HashMap<>();
        receita.put("natureza", "INCOME");
        receita.put("descricao", "Taxa " + valor);
        receita.put("idContaPlano", CONTA_TAXA_CONDOMINIAL);
        receita.put("valor", valor);
        receita.put("dataVencimento", dataPagamento.toString());
        String id = chamar("POST", base + "/lancamentos", receita, 201).get(0).get("resumo").get("id").asText();
        return chamar("POST", base + "/lancamentos/" + id + "/baixas", Map.of("idContaBancaria", conta,
                "dataPagamento", dataPagamento.toString(), "principal", valor, "formaPagamento", "PIX"), 200)
                .get("baixas").get(0).get("id").asText();
    }

    static String ofx(String numeroConta, String... transacoes) {
        return "OFXHEADER:100\nDATA:OFXSGML\nCHARSET:1252\n\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>"
                + "<BANKACCTFROM><BANKID>756<ACCTID>" + numeroConta + "</BANKACCTFROM><BANKTRANLIST>\n"
                + String.join("\n", transacoes) + "\n</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
    }

    static String trn(String fitId, LocalDate data, String valor, String memo) {
        return "<STMTTRN><TRNTYPE>OTHER<DTPOSTED>" + data.format(DateTimeFormatter.BASIC_ISO_DATE) + "<TRNAMT>" + valor
                + "<FITID>" + fitId + "<MEMO>" + memo + "</STMTTRN>";
    }

    private JsonNode linha(String fitId) throws Exception {
        for (JsonNode l : chamar("GET", base + "/contas-bancarias/" + conta + "/linhas-extrato", null, 200)) {
            if (l.get("fitId").asText().equals(fitId)) return l;
        }
        throw new AssertionError("linha não encontrada: " + fitId);
    }

    @Test
    void importacaoConciliaParesInequivocosEPermiteResolucaoManual() throws Exception {
        String baixa350 = baixaRecebida("350.00", d1);
        baixaRecebida("200.00", d1);
        String arquivo = ofx("0012345-6",
                trn("F1", d1.plusDays(1), "350,00", "PIX APTO 101"),
                trn("F2", d1, "200,00", "PIX APTO 102"),
                trn("F3", d1.plusDays(2), "-12,90", "TARIFA PACOTE"),
                trn("F4", d1.plusDays(2), "999,00", "TED NAO IDENTIFICADA"));

        JsonNode resultado = enviar(arquivo, 201);
        assertThat(resultado.get("linhasNovas").asInt()).isEqualTo(4);
        assertThat(resultado.get("conciliadasAutomaticamente").asInt()).isEqualTo(2);
        assertThat(resultado.get("pendentes").asInt()).isEqualTo(2);
        assertThat(linha("F1").get("situacao").asText()).isEqualTo("MATCHED");
        assertThat(linha("F1").get("idConciliado").asText()).isEqualTo(baixa350);

        enviar(arquivo, 409); // mesmo arquivo
        JsonNode sobreposto = enviar(ofx("12345-6", trn("F2", d1, "200,00", "PIX APTO 102"),
                trn("F5", d1.plusDays(3), "-5,00", "IOF")), 201);
        assertThat(sobreposto.get("duplicadas").asInt()).isEqualTo(1);
        assertThat(sobreposto.get("linhasNovas").asInt()).isEqualTo(1);

        // Tarifa só existe no extrato: lança, baixa e concilia de uma vez.
        JsonNode tarifa = chamar("POST", base + "/linhas-extrato/" + linha("F3").get("id").asText() + "/lancar",
                Map.of("idContaPlano", CONTA_TARIFAS_BANCARIAS, "descricao", "Tarifa bancária"), 200);
        assertThat(tarifa.get("situacao").asText()).isEqualTo("MATCHED");
        JsonNode extrato = chamar("GET", base + "/contas-bancarias/" + conta + "/extrato?de=" + d1.minusDays(1) + "&ate=" + LocalDate.now(),
                null, 200);
        assertThat(extrato.get("saldoFinal").decimalValue()).isEqualByComparingTo("1537.10");

        chamar("POST", base + "/linhas-extrato/" + linha("F4").get("id").asText() + "/ignorar",
                Map.of("observacao", "Devolvida ao remetente"), 200);
        assertThat(linha("F4").get("situacao").asText()).isEqualTo("IGNORED");

        // Desmarcar a conciliação no extrato do sistema libera a linha importada.
        chamar("POST", base + "/conciliar", Map.of("conciliada", false, "itens", List.of(Map.of("tipo", "PAYMENT", "id", baixa350))), 200);
        JsonNode liberada = linha("F1");
        assertThat(liberada.get("situacao").asText()).isEqualTo("UNMATCHED");
        assertThat(liberada.get("sugestoes").get(0).get("id").asText()).isEqualTo(baixa350);
        chamar("POST", base + "/linhas-extrato/" + liberada.get("id").asText() + "/conciliar",
                Map.of("tipo", "PAYMENT", "idMovimentacao", baixa350), 200);
        chamar("POST", base + "/linhas-extrato/" + liberada.get("id").asText() + "/conciliar",
                Map.of("tipo", "PAYMENT", "idMovimentacao", baixa350), 422);
    }

    @Test
    void rejeitaExtratoDeOutraContaEDeOutraAdministradora() throws Exception {
        enviar(ofx("99999-0", trn("X1", d1, "10,00", "PIX")), 422);
        String outra = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/contas-bancarias/" + conta + "/linhas-extrato").header("Authorization", outra))
                .andExpect(status().isNotFound());
    }
}
