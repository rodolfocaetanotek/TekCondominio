package br.com.tekcondo.acesso.service;

import java.time.Duration;

import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.integracao.email.MensagemEmail;
import br.com.tekcondo.comum.config.TekCondoProperties;
import org.springframework.stereotype.Component;

/**
 * Textos dos e-mails de conta. O token vai no fragmento da URL (#token=...), que o navegador não envia a
 * servidores nem inclui em cabeçalhos Referer — reduz o risco de vazamento em logs de proxies.
 */
@Component
public class EmailsConta {

    private final TekCondoProperties props;

    public EmailsConta(TekCondoProperties props) {
        this.props = props;
    }

    public MensagemEmail convite(Usuario usuario, String nomeAdministradora, String token) {
        String link = props.app().link("/definir-senha#token=" + token);
        String texto = """
                Olá, %s!

                Você foi convidado(a) para acessar o Tek Condomínio%s.

                Para ativar seu acesso, defina sua senha pelo link abaixo:
                %s

                O link é pessoal, pode ser usado uma única vez e vale por %s.
                Se você não esperava este convite, ignore esta mensagem.

                — Equipe Tek Condomínio
                """.formatted(primeiroNome(usuario), nomeAdministradora == null ? "" : " pela " + nomeAdministradora, link,
                descrever(props.security().inviteTtl()));
        return new MensagemEmail(usuario.getEmail(), "Convite de acesso ao Tek Condomínio", texto);
    }

    public MensagemEmail redefinicaoSenha(Usuario usuario, String token) {
        String link = props.app().link("/definir-senha#token=" + token);
        String texto = """
                Olá, %s!

                Recebemos uma solicitação para redefinir a senha da sua conta no Tek Condomínio.

                Para criar uma nova senha, acesse:
                %s

                O link pode ser usado uma única vez e vale por %s.
                Se você não fez esta solicitação, ignore este e-mail: sua senha atual continua válida.

                — Equipe Tek Condomínio
                """.formatted(primeiroNome(usuario), link, descrever(props.security().passwordResetTtl()));
        return new MensagemEmail(usuario.getEmail(), "Redefinição de senha — Tek Condomínio", texto);
    }

    private static String primeiroNome(Usuario usuario) {
        return usuario.getNome().split("\\s+")[0];
    }

    static String descrever(Duration duracao) {
        if (duracao.toDays() >= 1 && duracao.toHours() % 24 == 0) {
            long dias = duracao.toDays();
            return dias + (dias == 1 ? " dia" : " dias");
        }
        if (duracao.toHours() >= 1 && duracao.toMinutes() % 60 == 0) {
            long horas = duracao.toHours();
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return duracao.toMinutes() + " minutos";
    }
}
