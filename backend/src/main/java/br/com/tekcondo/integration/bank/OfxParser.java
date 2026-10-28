package br.com.tekcondo.integration.bank;

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

import br.com.tekcondo.shared.exception.ApiException;

/**
 * Leitor de extratos OFX 1.x (SGML, sem tags de fechamento nos campos) e 2.x (XML), como exportados pelos bancos
 * brasileiros. Lê apenas o necessário para conciliação: conta, período e transações (data, valor, FITID, histórico).
 * Aceita valores com ponto ou vírgula decimal (vários bancos usam vírgula).
 */
public final class OfxParser {

    public record Transaction(String fitId, LocalDate postedOn, BigDecimal amount, String type, String memo) {
    }

    public record Statement(String bankId, String accountId, LocalDate start, LocalDate end, List<Transaction> transactions) {
    }

    private static final Pattern TRANSACTION = Pattern.compile("<STMTTRN>(.*?)(?:</STMTTRN>|(?=<STMTTRN>)|(?=</BANKTRANLIST>))",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private OfxParser() {
    }

    public static Statement parse(byte[] content) {
        String text = decode(content);
        if (!text.toUpperCase(Locale.ROOT).contains("<OFX>")) {
            throw ApiException.badRequest("INVALID_OFX", "O arquivo não é um extrato OFX válido.");
        }
        List<Transaction> list = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        Matcher m = TRANSACTION.matcher(text);
        while (m.find()) {
            String block = m.group(1);
            LocalDate posted = date(field(block, "DTPOSTED"));
            BigDecimal amount = amount(field(block, "TRNAMT"));
            if (posted == null || amount == null) {
                throw ApiException.badRequest("INVALID_OFX", "Transação sem data ou valor no extrato OFX.");
            }
            if (amount.signum() == 0) {
                continue;
            }
            String memo = join(field(block, "NAME"), field(block, "MEMO"));
            String fitId = field(block, "FITID");
            if (fitId == null || fitId.isBlank()) {
                fitId = "H" + sha256((posted + "|" + amount.toPlainString() + "|" + memo).getBytes(StandardCharsets.UTF_8)).substring(0, 30);
            }
            // Alguns bancos repetem o FITID em transações distintas no mesmo arquivo.
            int n = seen.merge(fitId, 1, Integer::sum);
            if (n > 1) {
                fitId = fitId + "#" + n;
            }
            list.add(new Transaction(truncate(fitId, 100), posted, amount, field(block, "TRNTYPE"), truncate(memo, 255)));
        }
        return new Statement(field(text, "BANKID"), field(text, "ACCTID"), date(field(text, "DTSTART")), date(field(text, "DTEND")), list);
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------

    private static String decode(byte[] content) {
        String head = new String(content, 0, Math.min(content.length, 600), StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        Charset charset = head.contains("UTF-8") || head.contains("CHARSET:UTF") ? StandardCharsets.UTF_8 : Charset.forName("windows-1252");
        return new String(content, charset);
    }

    /** Valor de um campo-folha: em SGML vai até a próxima tag ou quebra de linha; em XML até a tag de fechamento. */
    private static String field(String block, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(block);
        if (!m.find()) {
            return null;
        }
        String value = m.group(1).trim();
        return value.isEmpty() ? null : unescape(value);
    }

    private static LocalDate date(String value) {
        if (value == null || value.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 8), DATE);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("INVALID_OFX", "Data inválida no extrato OFX: " + value);
        }
    }

    private static BigDecimal amount(String value) {
        if (value == null) {
            return null;
        }
        String v = value.replace(" ", "");
        if (v.contains(",") && v.contains(".")) {
            v = v.lastIndexOf(',') > v.lastIndexOf('.') ? v.replace(".", "").replace(',', '.') : v.replace(",", "");
        } else {
            v = v.replace(',', '.');
        }
        try {
            return new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("INVALID_OFX", "Valor inválido no extrato OFX: " + value);
        }
    }

    private static String join(String name, String memo) {
        if (name == null) return memo == null ? "" : memo;
        if (memo == null || memo.equalsIgnoreCase(name)) return name;
        return name + " — " + memo;
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
