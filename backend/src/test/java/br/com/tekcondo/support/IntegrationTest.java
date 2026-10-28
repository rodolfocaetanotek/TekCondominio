package br.com.tekcondo.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes de integração: sobe a aplicação completa contra um PostgreSQL 16 REAL (binários embarcados),
 * aplicando as migrações Flyway. Uma única instância é compartilhada por toda a suíte; cada teste usa dados
 * próprios (CNPJs/e-mails únicos), dispensando limpeza entre testes.
 */
@SpringBootTest
@Import({TestData.class, TestEmailConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestData data;

    private static EmbeddedPostgres start() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // encerramento da JVM
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao iniciar PostgreSQL embarcado", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
}
