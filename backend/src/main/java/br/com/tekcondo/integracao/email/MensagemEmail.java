package br.com.tekcondo.integracao.email;

/** Mensagem de e-mail em texto simples. */
public record MensagemEmail(String para, String assunto, String texto) {

    @Override
    public String toString() {
        // Corpo pode conter links com tokens: nunca deve ir para logs.
        return "MensagemEmail[para=" + para + ", assunto=" + assunto + "]";
    }
}
