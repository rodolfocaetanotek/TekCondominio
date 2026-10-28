package br.com.tekcondo.comum.validation;

import java.util.Locale;

/**
 * Normalização e validação de documentos brasileiros.
 *
 * <p>CNPJ: suporta o formato numérico tradicional e o alfanumérico (IN RFB nº 2.229/2024, vigente a partir de
 * julho/2026): as 12 primeiras posições aceitam [0-9A-Z] e os 2 dígitos verificadores continuam numéricos.
 * O valor de cada caractere no cálculo do módulo 11 é seu código ASCII menos 48 ('0' = 0 ... 'A' = 17).
 */
public final class DocumentosBrasileiros {

    private static final int[] PESOS_CNPJ_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private DocumentosBrasileiros() {
    }

    /** Remove máscara (pontos, barra, hífen, espaços) e converte para maiúsculas. Retorna null se vazio. */
    public static String normalizarCnpj(String bruto) {
        if (bruto == null) {
            return null;
        }
        String normalizado = bruto.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
        return normalizado.isEmpty() ? null : normalizado;
    }

    public static boolean isCnpjValido(String bruto) {
        String cnpj = normalizarCnpj(bruto);
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}[0-9]{2}")) {
            return false;
        }
        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = digitoVerificadorCnpj(cnpj, PESOS_CNPJ_1);
        int dv2 = digitoVerificadorCnpj(cnpj, PESOS_CNPJ_2);
        return dv1 == cnpj.charAt(12) - '0' && dv2 == cnpj.charAt(13) - '0';
    }

    private static int digitoVerificadorCnpj(String cnpj, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (cnpj.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    public static boolean isCpfValido(String bruto) {
        String cpf = somenteDigitos(bruto);
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        for (int posicao = 9; posicao <= 10; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += (cpf.charAt(i) - '0') * (posicao + 1 - i);
            }
            int digito = (soma * 10) % 11 % 10;
            if (digito != cpf.charAt(posicao) - '0') {
                return false;
            }
        }
        return true;
    }

    /** Normaliza CPF (11 dígitos) ou CNPJ (14 posições, inclusive alfanumérico). */
    public static String normalizarDocumento(String bruto) {
        String cnpj = normalizarCnpj(bruto);
        if (cnpj == null) {
            return null;
        }
        return cnpj.length() == 14 ? cnpj : somenteDigitos(bruto);
    }

    public static boolean isCpfOuCnpjValido(String bruto) {
        String doc = normalizarDocumento(bruto);
        return doc != null && (doc.length() == 11 ? isCpfValido(doc) : isCnpjValido(doc));
    }

    /** Mantém apenas dígitos. Retorna null se vazio. */
    public static String somenteDigitos(String bruto) {
        if (bruto == null) {
            return null;
        }
        String digitos = bruto.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /** Telefone brasileiro com DDD: 10 (fixo) ou 11 dígitos (celular). */
    public static boolean isTelefoneValido(String bruto) {
        String digitos = somenteDigitos(bruto);
        return digitos != null && digitos.matches("[1-9]{2}\\d{8,9}");
    }

    public static boolean isCepValido(String bruto) {
        String digitos = somenteDigitos(bruto);
        return digitos != null && digitos.length() == 8 && bruto.matches("\\d{5}-?\\d{3}");
    }
}
