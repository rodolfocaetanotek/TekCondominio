package br.com.tekcondo.integration.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envia e-mails somente APÓS o commit da transação: não se envia convite de usuário que acabou não sendo criado,
 * e uma falha do provedor não desfaz a operação de negócio (o envio pode ser repetido pelo usuário).
 */
@Service
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final MailGateway gateway;

    public MailDispatcher(MailGateway gateway) {
        this.gateway = gateway;
    }

    public void sendAfterCommit(MailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendNow(message);
                }
            });
        } else {
            sendNow(message);
        }
    }

    private void sendNow(MailMessage message) {
        try {
            gateway.send(message);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar {}", message, e);
        }
    }
}
