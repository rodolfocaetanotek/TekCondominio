package br.com.tekcondo.integracao.banco;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Gera linhas CNAB 240 (FEBRABAN) posicionais para testes. */
public final class Cnab240Fixture {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("ddMMyyyy");

    private Cnab240Fixture() {
    }

    public static String header(String bank, LocalDate generated) {
        StringBuilder s = blank();
        put(s, 0, bank);
        put(s, 7, "0");
        put(s, 143, generated.format(D));
        return s.toString();
    }

    public static String segmentT(int seq, String movement, String ourNumber, String yourNumber, LocalDate due, String face) {
        StringBuilder s = blank();
        put(s, 0, "756");
        put(s, 7, "3");
        put(s, 8, String.format("%05d", seq));
        put(s, 13, "T");
        put(s, 15, movement);
        put(s, 37, pad(ourNumber, 20));
        put(s, 58, pad(yourNumber, 15));
        put(s, 73, due.format(D));
        put(s, 81, num(face, 15));
        put(s, 198, num("1.50", 15));
        return s.toString();
    }

    public static String segmentU(int seq, String fees, String discount, String paid, LocalDate occurred, LocalDate credited) {
        StringBuilder s = blank();
        put(s, 0, "756");
        put(s, 7, "3");
        put(s, 8, String.format("%05d", seq));
        put(s, 13, "U");
        put(s, 17, num(fees, 15));
        put(s, 32, num(discount, 15));
        put(s, 47, num("0", 15));
        put(s, 77, num(paid, 15));
        put(s, 92, num(paid, 15));
        put(s, 137, occurred.format(D));
        put(s, 145, credited.format(D));
        return s.toString();
    }

    public static String trailer() {
        StringBuilder s = blank();
        put(s, 0, "756");
        put(s, 7, "9");
        return s.toString();
    }

    private static StringBuilder blank() {
        return new StringBuilder(" ".repeat(240));
    }

    private static void put(StringBuilder s, int pos, String v) {
        s.replace(pos, pos + v.length(), v);
    }

    private static String num(String valor, int len) {
        return String.format("%0" + len + "d", new BigDecimal(valor).movePointRight(2).longValueExact());
    }

    private static String pad(String v, int len) {
        return String.format("%-" + len + "s", v);
    }
}
