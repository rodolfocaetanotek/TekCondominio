package br.com.tekcondo.integracao.email;

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
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final EmailGateway gateway;

    public EmailDispatcher(EmailGateway gateway) {
        this.gateway = gateway;
    }

    public void enviarAposCommit(MensagemEmail mensagem) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarAgora(mensagem);
                }
            });
        } else {
            enviarAgora(mensagem);
        }
    }

    private void enviarAgora(MensagemEmail mensagem) {
        try {
            gateway.enviar(mensagem);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar {}", mensagem, e);
        }
    }
}
