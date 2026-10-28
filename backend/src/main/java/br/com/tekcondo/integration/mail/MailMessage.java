package br.com.tekcondo.integration.mail;

/** Mensagem de e-mail em texto simples. */
public record MailMessage(String to, String subject, String body) {

    @Override
    public String toString() {
        // Corpo pode conter links com tokens: nunca deve ir para logs.
        return "MailMessage[to=" + to + ", subject=" + subject + "]";
    }
}
