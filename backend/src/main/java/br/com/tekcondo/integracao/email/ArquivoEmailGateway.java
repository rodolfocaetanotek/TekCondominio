package br.com.tekcondo.integracao.email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.tekcondo.comum.config.TekCondoProperties;
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
public class ArquivoEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(ArquivoEmailGateway.class);
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path caixaSaida;

    public ArquivoEmailGateway(TekCondoProperties props) {
        this.caixaSaida = props.mail().outboxDir().toAbsolutePath();
        log.warn("E-mail em modo ARQUIVO (desenvolvimento): mensagens serão gravadas em {} e NÃO enviadas.", caixaSaida);
    }

    @Override
    public void enviar(MensagemEmail mensagem) {
        try {
            Files.createDirectories(caixaSaida);
            Path arquivo = caixaSaida.resolve(CARIMBO.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt");
            String conteudo = "Para: " + mensagem.para() + "\nAssunto: " + mensagem.assunto() + "\n\n" + mensagem.texto() + "\n";
            Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
            log.info("E-mail para {} gravado em {}", mensagem.para(), arquivo.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar e-mail na caixa de saída local", e);
        }
    }
}
