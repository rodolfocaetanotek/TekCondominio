package br.com.tekcondo.integration.bank;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import br.com.tekcondo.shared.exception.ApiException;

/**
 * Leitor do arquivo de retorno de cobrança CNAB 240 no layout padrão FEBRABAN (segmentos T e U).
 * Posições conforme o layout FEBRABAN v10; cada banco pode ter particularidades (ex.: composição do nosso número),
 * por isso o uso em produção exige homologação com o banco do condomínio — ver docs/INTEGRACOES.md.
 */
public final class Cnab240ReturnParser {

    /** Movimentos de liquidação (título pago). */
    public static final List<String> SETTLEMENT_CODES = List.of("06", "17");

    public record Item(int sequence, String movementCode, String ourNumber, String yourNumber, LocalDate dueDate,
                       BigDecimal faceValue, BigDecimal feesAndCharges, BigDecimal discount, BigDecimal paidAmount,
                       BigDecimal netAmount, BigDecimal bankFee, LocalDate occurrenceDate, LocalDate creditDate) {

        public boolean isSettlement() {
            return SETTLEMENT_CODES.contains(movementCode);
        }
    }

    public record ReturnFile(String bankCode, LocalDate generatedOn, List<Item> items) {
    }

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("ddMMyyyy");

    private Cnab240ReturnParser() {
    }

    public static ReturnFile parse(byte[] content) {
        String[] lines = new String(content, StandardCharsets.ISO_8859_1).split("\\r?\\n");
        String bank = null;
        LocalDate generated = null;
        List<Item> items = new ArrayList<>();
        String[] pendingT = null;
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            if (raw.isBlank()) {
                continue;
            }
            if (raw.length() < 240) {
                throw invalid("linha " + lineNo + " com " + raw.length() + " posições (esperado 240)");
            }
            String line = raw.substring(0, 240);
            char record = line.charAt(7);
            if (record == '0') {
                bank = line.substring(0, 3);
                generated = date(line, 143, lineNo);
            } else if (record == '3') {
                char segment = line.charAt(13);
                if (segment == 'T') {
                    pendingT = new String[]{line, String.valueOf(lineNo)};
                } else if (segment == 'U') {
                    if (pendingT == null) {
                        throw invalid("segmento U sem o segmento T correspondente na linha " + lineNo);
                    }
                    items.add(item(pendingT[0], line, lineNo));
                    pendingT = null;
                }
            }
        }
        if (bank == null) {
            throw invalid("header de arquivo (registro 0) ausente");
        }
        return new ReturnFile(bank, generated, items);
    }

    private static Item item(String t, String u, int lineNo) {
        return new Item(Integer.parseInt(t.substring(8, 13).trim().isEmpty() ? "0" : t.substring(8, 13).trim()),
                t.substring(15, 17), t.substring(37, 57).trim(), t.substring(58, 73).trim(), date(t, 73, lineNo),
                money(t, 81, 96), money(u, 17, 32), money(u, 32, 47).add(money(u, 47, 62)), money(u, 77, 92),
                money(u, 92, 107), money(t, 198, 213), date(u, 137, lineNo), date(u, 145, lineNo));
    }

    private static BigDecimal money(String line, int from, int to) {
        String digits = line.substring(from, to).trim();
        if (digits.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (!digits.chars().allMatch(Character::isDigit)) {
            throw invalid("valor não numérico nas posições " + (from + 1) + "-" + to);
        }
        return new BigDecimal(digits).movePointLeft(2);
    }

    private static LocalDate date(String line, int from, int lineNo) {
        String v = line.substring(from, from + 8).trim();
        if (v.isEmpty() || v.chars().allMatch(c -> c == '0')) {
            return null;
        }
        try {
            return LocalDate.parse(v, DDMMYYYY);
        } catch (RuntimeException e) {
            throw invalid("data inválida \"" + v + "\" na linha " + lineNo);
        }
    }

    private static ApiException invalid(String detail) {
        return ApiException.badRequest("INVALID_CNAB", "Arquivo de retorno CNAB 240 inválido: " + detail + ".");
    }
}
