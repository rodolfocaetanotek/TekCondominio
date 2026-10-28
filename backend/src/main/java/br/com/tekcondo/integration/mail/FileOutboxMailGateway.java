package br.com.tekcondo.integration.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.tekcondo.shared.config.TekCondoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SOMENTE DESENVOLVIMENTO: nenhum e-mail é enviado. Cada mensagem é gravada como arquivo .txt na pasta de
 * saída (padrão {@code backend/.local-dev/outbox}). O log informa apenas o arquivo, nunca o conteúdo.
 */
@Component
@ConditionalOnProperty(name = "tekcondo.mail.provider", havingValue = "file")
public class FileOutboxMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(FileOutboxMailGateway.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path outbox;

    public FileOutboxMailGateway(TekCondoProperties props) {
        this.outbox = props.mail().outboxDir().toAbsolutePath();
        log.warn("E-mail em modo ARQUIVO (desenvolvimento): mensagens serão gravadas em {} e NÃO enviadas.", outbox);
    }

    @Override
    public void send(MailMessage message) {
        try {
            Files.createDirectories(outbox);
            Path file = outbox.resolve(STAMP.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt");
            String content = "Para: " + message.to() + "\nAssunto: " + message.subject() + "\n\n" + message.body() + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8);
            log.info("E-mail para {} gravado em {}", message.to(), file.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar e-mail na caixa de saída local", e);
        }
    }
}
