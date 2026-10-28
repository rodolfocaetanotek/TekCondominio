package br.com.tekcondo.identity;

import java.util.Arrays;
import java.util.List;

import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que o enum Permission e a tabela permission (migrações) estão sincronizados. */
class PermissionCatalogIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void enumMatchesDatabaseCatalog() {
        List<String> db = jdbc.queryForList("select code from permission", String.class);
        List<String> code = Arrays.stream(Permission.values()).map(Enum::name).toList();

        assertThat(db).containsExactlyInAnyOrderElementsOf(code);
    }

    @Test
    void openApiDocumentationIsPublished() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paths['/api/v1/condominiums']").exists());
    }
}
