package br.com.tekcondo.acesso;

import java.util.Arrays;
import java.util.List;

import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que o enum Permissao e a tabela permission (migrações) estão sincronizados. */
class CatalogoPermissoesIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void enumCorrespondeAoCatalogoDoBanco() {
        List<String> db = jdbc.queryForList("select code from permission", String.class);
        List<String> codigo = Arrays.stream(Permissao.values()).map(Enum::name).toList();

        assertThat(db).containsExactlyInAnyOrderElementsOf(codigo);
    }

    @Test
    void documentacaoOpenApiEPublicada() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paths['/api/v1/condominios']").exists());
    }
}
