package br.com.tekcondo.shared.io;

import java.math.BigDecimal;

/** Conversão de números digitados no padrão brasileiro ("1.234,56") ou internacional ("1234.56"). */
public final class BrazilianNumbers {

    private BrazilianNumbers() {
    }

    /** @throws NumberFormatException se o texto não for um número */
    public static BigDecimal parse(String raw) {
        String s = raw.trim().replace(" ", "").replace("%", "");
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(s);
    }
}
