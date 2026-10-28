package br.com.tekcondo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

/**
 * Execução local SEM Docker (somente desenvolvimento): sobe um PostgreSQL 16 real embarcado, com dados
 * persistidos em {@code backend/.local-dev/pgdata}, e inicia a API no perfil dev.
 *
 * <pre>mvn spring-boot:test-run</pre>
 *
 * Se TEKCONDO_BOOTSTRAP_ADMIN_EMAIL/PASSWORD não estiverem definidos, gera credenciais aleatórias do administrador
 * da plataforma na primeira execução e as grava em {@code backend/.local-dev/credentials.txt} (fora do git).
 * Para uma segunda instância isolada (ex.: validação ponta a ponta): {@code -Dtekcondo.localdev.dir=.local-dev-e2e
 * -Dtekcondo.localdev.pg-port=54339 -Dserver.port=8090}.
 */
public final class LocalDevApplication {

    private static final int PG_PORT = Integer.getInteger("tekcondo.localdev.pg-port", 54329);

    private LocalDevApplication() {
    }

    public static void main(String[] args) throws IOException {
        Path base = Path.of(System.getProperty("tekcondo.localdev.dir", ".local-dev")).toAbsolutePath();
        Files.createDirectories(base);
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(base.resolve("pgdata"))
                .setCleanDataDirectory(false)
                .setPort(PG_PORT)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (IOException ignored) {
                // encerramento
            }
        }));

        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        System.setProperty("spring.profiles.active", "dev");
        System.setProperty("tekcondo.storage.dir", base.resolve("storage").toString());
        System.setProperty("tekcondo.mail.outbox-dir", base.resolve("outbox").toString());
        configureDevAdmin(base);

        SpringApplication.run(TekCondoApplication.class, args);
    }

    private static void configureDevAdmin(Path base) throws IOException {
        if (System.getenv("TEKCONDO_BOOTSTRAP_ADMIN_EMAIL") != null) {
            return;
        }
        Path file = base.resolve("credentials.txt");
        String email = "admin@tekcondo.local";
        String password;
        if (Files.exists(file)) {
            List<String> lines = Files.readAllLines(file);
            password = lines.get(1).substring("senha: ".length());
        } else {
            byte[] bytes = new byte[8];
            new SecureRandom().nextBytes(bytes);
            password = "Dev" + HexFormat.of().formatHex(bytes) + "9";
            Files.write(file, List.of("email: " + email, "senha: " + password));
        }
        System.setProperty("tekcondo.bootstrap.admin-email", email);
        System.setProperty("tekcondo.bootstrap.admin-password", password);
        System.out.println("\n>>> Administrador da plataforma (dev): credenciais em " + file + "\n");
    }
}
