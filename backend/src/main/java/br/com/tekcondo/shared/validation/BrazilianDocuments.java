package br.com.tekcondo.shared.validation;

import java.util.Locale;

/**
 * Normalização e validação de documentos brasileiros.
 *
 * <p>CNPJ: suporta o formato numérico tradicional e o alfanumérico (IN RFB nº 2.229/2024, vigente a partir de
 * julho/2026): as 12 primeiras posições aceitam [0-9A-Z] e os 2 dígitos verificadores continuam numéricos.
 * O valor de cada caractere no cálculo do módulo 11 é seu código ASCII menos 48 ('0' = 0 ... 'A' = 17).
 */
public final class BrazilianDocuments {

    private static final int[] CNPJ_WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private BrazilianDocuments() {
    }

    /** Remove máscara (pontos, barra, hífen, espaços) e converte para maiúsculas. Retorna null se vazio. */
    public static String normalizeCnpj(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isValidCnpj(String raw) {
        String cnpj = normalizeCnpj(raw);
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}[0-9]{2}")) {
            return false;
        }
        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = cnpjCheckDigit(cnpj, CNPJ_WEIGHTS_1);
        int dv2 = cnpjCheckDigit(cnpj, CNPJ_WEIGHTS_2);
        return dv1 == cnpj.charAt(12) - '0' && dv2 == cnpj.charAt(13) - '0';
    }

    private static int cnpjCheckDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    public static boolean isValidCpf(String raw) {
        String cpf = digitsOnly(raw);
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        for (int position = 9; position <= 10; position++) {
            int sum = 0;
            for (int i = 0; i < position; i++) {
                sum += (cpf.charAt(i) - '0') * (position + 1 - i);
            }
            int digit = (sum * 10) % 11 % 10;
            if (digit != cpf.charAt(position) - '0') {
                return false;
            }
        }
        return true;
    }

    /** Normaliza CPF (11 dígitos) ou CNPJ (14 posições, inclusive alfanumérico). */
    public static String normalizeDocument(String raw) {
        String cnpj = normalizeCnpj(raw);
        if (cnpj == null) {
            return null;
        }
        return cnpj.length() == 14 ? cnpj : digitsOnly(raw);
    }

    public static boolean isValidCpfOrCnpj(String raw) {
        String doc = normalizeDocument(raw);
        return doc != null && (doc.length() == 11 ? isValidCpf(doc) : isValidCnpj(doc));
    }

    /** Mantém apenas dígitos. Retorna null se vazio. */
    public static String digitsOnly(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Telefone brasileiro com DDD: 10 (fixo) ou 11 dígitos (celular). */
    public static boolean isValidPhone(String raw) {
        String digits = digitsOnly(raw);
        return digits != null && digits.matches("[1-9]{2}\\d{8,9}");
    }

    public static boolean isValidZipCode(String raw) {
        String digits = digitsOnly(raw);
        return digits != null && digits.length() == 8 && raw.matches("\\d{5}-?\\d{3}");
    }
}
