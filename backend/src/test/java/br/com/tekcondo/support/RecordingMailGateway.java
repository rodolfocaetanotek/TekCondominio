package br.com.tekcondo.support;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.tekcondo.integration.mail.MailGateway;
import br.com.tekcondo.integration.mail.MailMessage;

/** Captura os e-mails enviados durante os testes de integração. */
public class RecordingMailGateway implements MailGateway {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        sent.add(message);
    }

    public List<MailMessage> to(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    /** Token do e-mail mais recente enviado ao destinatário. */
    public Optional<String> lastTokenFor(String email) {
        List<MailMessage> messages = to(email);
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = TOKEN.matcher(messages.getLast().body());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
