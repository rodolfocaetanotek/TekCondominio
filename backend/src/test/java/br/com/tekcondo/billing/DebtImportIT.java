package br.com.tekcondo.billing;

import java.nio.charset.StandardCharsets;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.organization.entity.Organization;
import br.com.tekcondo.support.IntegrationTest;
import br.com.tekcondo.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DebtImportIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    Condominium condo;
    String token;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        Organization org = data.organization();
        condo = data.condominium(org, "Residencial Ipê");
        token = TestData.bearer(data.login(data.orgAdmin(org)));
        base = "/api/v1/condominiums/" + condo.getId();
        mvc.perform(multipart(base + "/units/import").file(csv("""
                        bloco;unidade;fracao;proprietario;documento
                        A;101;50;Ana Souza;529.982.247-25
                        A;102;50;;
                        """)).header("Authorization", token))
                .andExpect(jsonPath("$.unitsCreated").value(2));
    }

    @Test
    void importsOpenDebtsAllOrNothing() throws Exception {
        String bad = """
                bloco;unidade;competencia;vencimento;valor;documento
                A;101;08/2026;10/08/2026;450,00;X-1
                A;999;08/2026;10/08/2026;450,00;X-2
                A;102;13/2026;10/08/2026;-1;X-3
                A;102;08/2026;10/08/2026;100,00;X-1
                """;
        mvc.perform(multipart(base + "/billing/debts/import").file(csv(bad)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debts").value(0))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[0].line").value(3));
        assertThat(importedCount()).isZero();

        String good = """
                Bloco;Unidade;Competência;Vencimento;Valor;Descrição;Documento
                A;101;08/2026;10/08/2026;450,00;Taxa condominial 08/2026;X-1
                A;101;09/2026;10/09/2026;450,00;;X-2
                A;102;09/2026;10/09/2026;1.200,50;Taxa extra;X-3
                """;
        mvc.perform(multipart(base + "/billing/debts/import").file(csv(good)).param("dryRun", "true").header("Authorization", token))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.debts").value(3))
                .andExpect(jsonPath("$.total").value(2100.50));
        assertThat(importedCount()).isZero();

        mvc.perform(multipart(base + "/billing/debts/import").file(csv(good)).header("Authorization", token))
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.debts").value(3))
                .andExpect(jsonPath("$.errors", hasSize(0)));
        assertThat(importedCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                select p.name from financial_entry e join person p on p.id = e.person_id
                where e.condominium_id = ? and e.document_number = 'X-1'
                """, String.class, condo.getId())).isEqualTo("Ana Souza");

        mvc.perform(get(base + "/billing/delinquency").param("asOf", "2026-10-28").header("Authorization", token))
                .andExpect(jsonPath("$.rows", hasSize(2)));

        mvc.perform(multipart(base + "/billing/debts/import").file(csv(good)).header("Authorization", token))
                .andExpect(jsonPath("$.debts").value(0))
                .andExpect(jsonPath("$.errors", hasSize(3)));
        assertThat(importedCount()).isEqualTo(3);
    }

    private int importedCount() {
        return jdbc.queryForObject("select count(*) from financial_entry where condominium_id = ? and origin = 'IMPORT'",
                Integer.class, condo.getId());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "debitos.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
