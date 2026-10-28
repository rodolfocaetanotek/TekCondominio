package br.com.tekcondo.integracao.email;

/**
 * Porta de saída para e-mail. A aplicação depende só desta interface; o provedor concreto (SMTP, serviço
 * transacional, arquivo em dev) é escolhido por configuração ({@code tekcondo.mail.provider}).
 */
public interface EmailGateway {

    void enviar(MensagemEmail mensagem);
}
