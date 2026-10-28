package br.com.tekcondo.identity.service;

import java.time.Duration;

import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.integration.mail.MailMessage;
import br.com.tekcondo.shared.config.TekCondoProperties;
import org.springframework.stereotype.Component;

/**
 * Textos dos e-mails de conta. O token vai no fragmento da URL (#token=...), que o navegador não envia a
 * servidores nem inclui em cabeçalhos Referer — reduz o risco de vazamento em logs de proxies.
 */
@Component
public class AccountEmails {

    private final TekCondoProperties props;

    public AccountEmails(TekCondoProperties props) {
        this.props = props;
    }

    public MailMessage invite(User user, String organizationName, String token) {
        String link = props.app().link("/definir-senha#token=" + token);
        String body = """
                Olá, %s!

                Você foi convidado(a) para acessar o Tek Condomínio%s.

                Para ativar seu acesso, defina sua senha pelo link abaixo:
                %s

                O link é pessoal, pode ser usado uma única vez e vale por %s.
                Se você não esperava este convite, ignore esta mensagem.

                — Equipe Tek Condomínio
                """.formatted(firstName(user), organizationName == null ? "" : " pela " + organizationName, link,
                describe(props.security().inviteTtl()));
        return new MailMessage(user.getEmail(), "Convite de acesso ao Tek Condomínio", body);
    }

    public MailMessage passwordReset(User user, String token) {
        String link = props.app().link("/definir-senha#token=" + token);
        String body = """
                Olá, %s!

                Recebemos uma solicitação para redefinir a senha da sua conta no Tek Condomínio.

                Para criar uma nova senha, acesse:
                %s

                O link pode ser usado uma única vez e vale por %s.
                Se você não fez esta solicitação, ignore este e-mail: sua senha atual continua válida.

                — Equipe Tek Condomínio
                """.formatted(firstName(user), link, describe(props.security().passwordResetTtl()));
        return new MailMessage(user.getEmail(), "Redefinição de senha — Tek Condomínio", body);
    }

    private static String firstName(User user) {
        return user.getName().split("\\s+")[0];
    }

    static String describe(Duration d) {
        if (d.toDays() >= 1 && d.toHours() % 24 == 0) {
            long days = d.toDays();
            return days + (days == 1 ? " dia" : " dias");
        }
        if (d.toHours() >= 1 && d.toMinutes() % 60 == 0) {
            long hours = d.toHours();
            return hours + (hours == 1 ? " hora" : " horas");
        }
        return d.toMinutes() + " minutos";
    }
}
