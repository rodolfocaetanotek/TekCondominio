package br.com.tekcondo.support;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.tekcondo.integracao.email.EmailGateway;
import br.com.tekcondo.integracao.email.MensagemEmail;

/** Captura os e-mails enviados durante os testes de integração. */
public class GravacaoEmailGateway implements EmailGateway {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private final List<MensagemEmail> enviadas = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(MensagemEmail mensagem) {
        enviadas.add(mensagem);
    }

    public List<MensagemEmail> para(String email) {
        return enviadas.stream().filter(m -> m.para().equalsIgnoreCase(email)).toList();
    }

    /** Token do e-mail mais recente enviado ao destinatário. */
    public Optional<String> ultimoTokenPara(String email) {
        List<MensagemEmail> mensagens = para(email);
        if (mensagens.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = TOKEN.matcher(mensagens.getLast().texto());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
