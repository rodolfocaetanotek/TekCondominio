package br.com.tekcondo.comum.io;

import java.math.BigDecimal;

/** Conversão de números digitados no padrão brasileiro ("1.234,56") ou internacional ("1234.56"). */
public final class NumerosBrasileiros {

    private NumerosBrasileiros() {
    }

    /** @throws NumberFormatException se o texto não for um número */
    public static BigDecimal converter(String bruto) {
        String s = bruto.trim().replace(" ", "").replace("%", "");
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(s);
    }
}
