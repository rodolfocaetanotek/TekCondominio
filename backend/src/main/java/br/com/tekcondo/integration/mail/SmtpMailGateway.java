package br.com.tekcondo.integration.mail;

import br.com.tekcondo.shared.config.TekCondoProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envio real via SMTP (configurado por spring.mail.* / TEKCONDO_SMTP_*). */
@Component
@ConditionalOnProperty(name = "tekcondo.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender sender;
    private final MailProperties mailProperties;
    private final TekCondoProperties props;

    public SmtpMailGateway(JavaMailSender sender, MailProperties mailProperties, TekCondoProperties props) {
        this.sender = sender;
        this.mailProperties = mailProperties;
        this.props = props;
    }

    @PostConstruct
    void verifyConfiguration() {
        if (mailProperties.getHost() == null || mailProperties.getHost().isBlank()) {
            throw new IllegalStateException("Provedor de e-mail SMTP sem TEKCONDO_SMTP_HOST configurado. "
                    + "Configure o SMTP ou use TEKCONDO_MAIL_PROVIDER=file apenas em desenvolvimento.");
        }
    }

    @Override
    public void send(MailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(props.mail().from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        sender.send(mail);
    }
}
