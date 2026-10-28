package br.com.tekcondo.cobranca;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
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

import static br.com.tekcondo.integracao.banco.Cnab240Fixture.header;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.segmentT;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.segmentU;
import static br.com.tekcondo.integracao.banco.Cnab240Fixture.trailer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetornoBancarioIT extends IntegrationTest {

    static final String CONTA_TAXA_CONDOMINIAL = "20000000-0000-0000-0000-000000000101";

    @Autowired
    ObjectMapper objectMapper;

    Administradora administradora;
    Condominio condominio;
    String token;
    String base;
    String contaBancaria;
    String unidade;
    LocalDate hoje = LocalDate.now();
    LocalDate vencimento = hoje.minusDays(10);

    @BeforeEach
    void setUp() throws Exception {
        administradora = data.administradora();
        condominio = data.condominio(administradora, "Condomínio Retorno");
        token = TestData.bearer(data.login(data.adminAdministradora(administradora)));
        base = "/api/v1/condominios/" + condominio.getId();
        contaBancaria = chamar("POST", base + "/financeiro/contas-bancarias", Map.of("nome", "Sicoob Cobrança", "tipo", "CHECKING", "codigoBanco", "756",
                "saldoInicial", "0", "dataAbertura", hoje.minusMonths(2).toString()), 201).get("id").asText();
        unidade = chamar("POST", base + "/unidades", Map.of("identificacao", "101", "tipo", "APARTMENT", "fracaoIdeal", "10"), 201).get("id").asText();
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

    private JsonNode enviar(String idConta, String conteudo, int esperado) throws Exception {
        String json = mvc.perform(multipart(base + "/cobranca/retornos-bancarios")
                        .file(new MockMultipartFile("arquivo", "COB.RET", "text/plain", conteudo.getBytes(StandardCharsets.ISO_8859_1)))
                        .param("idContaBancaria", idConta).header("Authorization", token))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode cobranca(String valor) throws Exception {
        Map<String, Object> receita = new HashMap<>();
        receita.put("natureza", "INCOME");
        receita.put("descricao", "Taxa condominial");
        receita.put("idContaPlano", CONTA_TAXA_CONDOMINIAL);
        receita.put("valor", valor);
        receita.put("dataVencimento", vencimento.toString());
        receita.put("idUnidade", unidade);
        return chamar("POST", base + "/financeiro/lancamentos", receita, 201).get(0).get("resumo");
    }

    @Test
    void liquidacoesSaoBaixadasUmaVezSeparandoMultaEJuros() throws Exception {
        JsonNode primeira = cobranca("500.00");
        JsonNode segunda = cobranca("500.00");
        long numero = primeira.get("numeroCobranca").asLong();
        assertThat(segunda.get("numeroCobranca").asLong()).isNotEqualTo(numero);

        LocalDate pagoEm = hoje.minusDays(2);
        String arquivo = String.join("\r\n", header("756", hoje),
                segmentT(1, "06", "", String.valueOf(numero), vencimento, "500.00"),
                segmentU(2, "13.40", "0", "513.40", pagoEm, pagoEm.plusDays(1)),
                segmentT(3, "06", "", "99999999", vencimento, "500.00"),
                segmentU(4, "0", "0", "500.00", pagoEm, pagoEm),
                segmentT(5, "02", "", String.valueOf(segunda.get("numeroCobranca").asLong()), vencimento, "500.00"),
                segmentU(6, "0", "0", "0", pagoEm, pagoEm),
                trailer());

        JsonNode resultado = enviar(contaBancaria, arquivo, 201);
        assertThat(resultado.get("baixados").asInt()).isEqualTo(1);
        assertThat(resultado.get("pendentes").asInt()).isEqualTo(1);
        enviar(contaBancaria, arquivo, 409);

        JsonNode lancamento = chamar("GET", base + "/financeiro/lancamentos/" + primeira.get("id").asText(), null, 200);
        assertThat(lancamento.get("resumo").get("situacao").asText()).isEqualTo("PAID");
        JsonNode baixa = lancamento.get("baixas").get(0);
        assertThat(baixa.get("formaPagamento").asText()).isEqualTo("BOLETO");
        assertThat(baixa.get("multa").decimalValue()).isEqualByComparingTo("10.00"); // 2% de 500
        assertThat(baixa.get("juros").decimalValue()).isEqualByComparingTo("3.40");
        assertThat(baixa.get("total").decimalValue()).isEqualByComparingTo("513.40");
        assertThat(baixa.get("dataPagamento").asText()).isEqualTo(pagoEm.plusDays(1).toString()); // data do crédito

        JsonNode itens = chamar("GET", base + "/cobranca/retornos-bancarios/" + resultado.get("id").asText() + "/itens", null, 200);
        assertThat(itens.get(2).get("situacao").asText()).isEqualTo("INFO");
        assertThat(itens.get(2).get("descricaoMovimento").asText()).isEqualTo("Entrada confirmada");

        // Outro arquivo repetindo a mesma liquidação: não baixa de novo.
        String repetido = String.join("\r\n", header("756", hoje.minusDays(1)),
                segmentT(1, "06", "", String.valueOf(numero), vencimento, "500.00"),
                segmentU(2, "13.40", "0", "513.40", pagoEm, pagoEm.plusDays(1)), trailer());
        assertThat(enviar(contaBancaria, repetido, 201).get("baixados").asInt()).isZero();

        // Pendência "não localizada" é baixada manualmente na cobrança certa.
        JsonNode pendentes = chamar("GET", base + "/cobranca/itens-retorno/pendentes", null, 200);
        assertThat(pendentes).hasSize(2);
        String naoLocalizado = null;
        for (JsonNode p : pendentes) {
            if (p.get("seuNumero").asText().equals("99999999")) naoLocalizado = p.get("id").asText();
        }
        JsonNode baixado = chamar("POST", base + "/cobranca/itens-retorno/" + naoLocalizado + "/baixar",
                Map.of("idLancamento", segunda.get("id").asText()), 200);
        assertThat(baixado.get("situacao").asText()).isEqualTo("SETTLED");
        assertThat(chamar("GET", base + "/financeiro/lancamentos/" + segunda.get("id").asText(), null, 200)
                .get("resumo").get("situacao").asText()).isEqualTo("PAID");
        chamar("POST", base + "/cobranca/itens-retorno/" + naoLocalizado + "/baixar", Map.of("idLancamento", segunda.get("id").asText()), 422);
    }

    @Test
    void recusaRetornoDeOutroBancoEDeOutraAdministradora() throws Exception {
        String bb = chamar("POST", base + "/financeiro/contas-bancarias", Map.of("nome", "Banco do Brasil", "tipo", "CHECKING", "codigoBanco", "001",
                "saldoInicial", "0", "dataAbertura", hoje.minusMonths(2).toString()), 201).get("id").asText();
        enviar(bb, String.join("\n", header("756", hoje), trailer()), 422);

        String outraAdministradora = TestData.bearer(data.login(data.adminAdministradora(data.administradora())));
        mvc.perform(get(base + "/cobranca/retornos-bancarios").header("Authorization", outraAdministradora)).andExpect(status().isNotFound());
    }
}
