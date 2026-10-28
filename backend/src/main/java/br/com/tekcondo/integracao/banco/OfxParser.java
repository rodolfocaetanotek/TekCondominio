package br.com.tekcondo.integracao.banco;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.tekcondo.comum.exception.ApiException;

/**
 * Leitor de extratos OFX 1.x (SGML, sem tags de fechamento nos campos) e 2.x (XML), como exportados pelos bancos
 * brasileiros. Lê apenas o necessário para conciliação: conta, período e transações (data, valor, FITID, histórico).
 * Aceita valores com ponto ou vírgula decimal (vários bancos usam vírgula).
 */
public final class OfxParser {

    public record Transacao(String fitId, LocalDate dataLancamento, BigDecimal valor, String tipo, String historico) {
    }

    public record Extrato(String idBanco, String idConta, LocalDate inicio, LocalDate fim, List<Transacao> transacoes) {
    }

    private static final Pattern TRANSACAO = Pattern.compile("<STMTTRN>(.*?)(?:</STMTTRN>|(?=<STMTTRN>)|(?=</BANKTRANLIST>))",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter DATA = DateTimeFormatter.BASIC_ISO_DATE;

    private OfxParser() {
    }

    public static Extrato parse(byte[] conteudo) {
        String texto = decodificar(conteudo);
        if (!texto.toUpperCase(Locale.ROOT).contains("<OFX>")) {
            throw ApiException.requisicaoInvalida("INVALID_OFX", "O arquivo não é um extrato OFX válido.");
        }
        List<Transacao> lista = new ArrayList<>();
        Map<String, Integer> vistos = new HashMap<>();
        Matcher m = TRANSACAO.matcher(texto);
        while (m.find()) {
            String bloco = m.group(1);
            LocalDate lancamento = data(campo(bloco, "DTPOSTED"));
            BigDecimal valor = valor(campo(bloco, "TRNAMT"));
            if (lancamento == null || valor == null) {
                throw ApiException.requisicaoInvalida("INVALID_OFX", "Transação sem data ou valor no extrato OFX.");
            }
            if (valor.signum() == 0) {
                continue;
            }
            String historico = juntar(campo(bloco, "NAME"), campo(bloco, "MEMO"));
            String fitId = campo(bloco, "FITID");
            if (fitId == null || fitId.isBlank()) {
                fitId = "H" + sha256((lancamento + "|" + valor.toPlainString() + "|" + historico).getBytes(StandardCharsets.UTF_8)).substring(0, 30);
            }
            // Alguns bancos repetem o FITID em transações distintas no mesmo arquivo.
            int n = vistos.merge(fitId, 1, Integer::sum);
            if (n > 1) {
                fitId = fitId + "#" + n;
            }
            lista.add(new Transacao(truncar(fitId, 100), lancamento, valor, campo(bloco, "TRNTYPE"), truncar(historico, 255)));
        }
        return new Extrato(campo(texto, "BANKID"), campo(texto, "ACCTID"), data(campo(texto, "DTSTART")), data(campo(texto, "DTEND")), lista);
    }

    public static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------

    private static String decodificar(byte[] conteudo) {
        String cabecalho = new String(conteudo, 0, Math.min(conteudo.length, 600), StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        Charset charset = cabecalho.contains("UTF-8") || cabecalho.contains("CHARSET:UTF") ? StandardCharsets.UTF_8 : Charset.forName("windows-1252");
        return new String(conteudo, charset);
    }

    /** Valor de um campo-folha: em SGML vai até a próxima tag ou quebra de linha; em XML até a tag de fechamento. */
    private static String campo(String bloco, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(bloco);
        if (!m.find()) {
            return null;
        }
        String texto = m.group(1).trim();
        return texto.isEmpty() ? null : desescapar(texto);
    }

    private static LocalDate data(String texto) {
        if (texto == null || texto.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(texto.substring(0, 8), DATA);
        } catch (RuntimeException e) {
            throw ApiException.requisicaoInvalida("INVALID_OFX", "Data inválida no extrato OFX: " + texto);
        }
    }

    private static BigDecimal valor(String texto) {
        if (texto == null) {
            return null;
        }
        String v = texto.replace(" ", "");
        if (v.contains(",") && v.contains(".")) {
            v = v.lastIndexOf(',') > v.lastIndexOf('.') ? v.replace(".", "").replace(',', '.') : v.replace(",", "");
        } else {
            v = v.replace(',', '.');
        }
        try {
            return new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw ApiException.requisicaoInvalida("INVALID_OFX", "Valor inválido no extrato OFX: " + texto);
        }
    }

    private static String juntar(String nome, String historico) {
        if (nome == null) return historico == null ? "" : historico;
        if (historico == null || historico.equalsIgnoreCase(nome)) return nome;
        return nome + " — " + historico;
    }

    private static String desescapar(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String truncar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
