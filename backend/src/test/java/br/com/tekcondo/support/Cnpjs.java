package br.com.tekcondo.support;

import java.util.concurrent.ThreadLocalRandom;

/** Gera CNPJs numéricos válidos e únicos para os testes. */
public final class Cnpjs {

    private static final int[] W1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] W2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpjs() {
    }

    public static String random() {
        StringBuilder base = new StringBuilder();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            base.append(rnd.nextInt(10));
        }
        base.append("0001");
        base.append(dv(base, W1));
        base.append(dv(base, W2));
        return base.toString();
    }

    private static int dv(CharSequence s, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (s.charAt(i) - '0') * weights[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }
}
