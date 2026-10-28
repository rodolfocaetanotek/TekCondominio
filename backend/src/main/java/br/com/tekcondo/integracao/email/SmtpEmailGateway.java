package br.com.tekcondo.integracao.email;

import br.com.tekcondo.comum.config.TekCondoProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envio real via SMTP (configurado por spring.mail.* / TEKCONDO_SMTP_*). */
@Component
@ConditionalOnProperty(name = "tekcondo.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender sender;
    private final MailProperties mailProperties;
    private final TekCondoProperties props;

    public SmtpEmailGateway(JavaMailSender sender, MailProperties mailProperties, TekCondoProperties props) {
        this.sender = sender;
        this.mailProperties = mailProperties;
        this.props = props;
    }

    @PostConstruct
    void verificarConfiguracao() {
        if (mailProperties.getHost() == null || mailProperties.getHost().isBlank()) {
            throw new IllegalStateException("Provedor de e-mail SMTP sem TEKCONDO_SMTP_HOST configurado. "
                    + "Configure o SMTP ou use TEKCONDO_MAIL_PROVIDER=file apenas em desenvolvimento.");
        }
    }

    @Override
    public void enviar(MensagemEmail mensagem) {
        SimpleMailMessage smtp = new SimpleMailMessage();
        smtp.setFrom(props.mail().from());
        smtp.setTo(mensagem.para());
        smtp.setSubject(mensagem.assunto());
        smtp.setText(mensagem.texto());
        sender.send(smtp);
    }
}
