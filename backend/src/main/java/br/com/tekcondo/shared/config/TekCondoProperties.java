package br.com.tekcondo.shared.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "tekcondo")
public record TekCondoProperties(Security security, Bootstrap bootstrap, @DefaultValue App app, @DefaultValue Mail mail,
                                 @DefaultValue Storage storage) {

    /** Armazenamento de arquivos (documentos e anexos). */
    public record Storage(@DefaultValue(".local-dev/storage") Path dir,
                          @DefaultValue("10485760") long maxFileBytes) {
    }

    public record Security(
            String jwtSecret,
            @DefaultValue("false") boolean allowEphemeralJwtSecret,
            @DefaultValue("PT15M") Duration accessTokenTtl,
            @DefaultValue("P7D") Duration refreshTokenTtl,
            @DefaultValue("true") boolean refreshCookieSecure,
            @DefaultValue("5") int maxFailedLogins,
            @DefaultValue("PT15M") Duration lockoutDuration,
            @DefaultValue("PT1H") Duration passwordResetTtl,
            @DefaultValue("P3D") Duration inviteTtl,
            @DefaultValue("3") int maxResetRequestsPerHour,
            List<String> corsAllowedOrigins) {

        @Override
        public List<String> corsAllowedOrigins() {
            return corsAllowedOrigins == null ? List.of()
                    : corsAllowedOrigins.stream().filter(o -> o != null && !o.isBlank()).toList();
        }
    }

    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {
    }

    /** URL pública do frontend, usada nos links enviados por e-mail. */
    public record App(@DefaultValue("http://localhost:4200") String publicUrl) {

        public String link(String path) {
            String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            return base + path;
        }
    }

    /**
     * Envio de e-mail. {@code provider}: {@code smtp} (produção, usa spring.mail.*) ou {@code file}
     * (desenvolvimento: grava as mensagens em arquivos na pasta {@code outboxDir}, nada é enviado).
     */
    public record Mail(@DefaultValue("smtp") String provider,
                       @DefaultValue("Tek Condomínio <nao-responda@tekcondo.local>") String from,
                       @DefaultValue(".local-dev/outbox") Path outboxDir) {
    }
}
