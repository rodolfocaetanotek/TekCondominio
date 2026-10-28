package br.com.tekcondo.integration.mail;

/**
 * Porta de saída para e-mail. A aplicação depende só desta interface; o provedor concreto (SMTP, serviço
 * transacional, arquivo em dev) é escolhido por configuração ({@code tekcondo.mail.provider}).
 */
public interface MailGateway {

    void send(MailMessage message);
}
