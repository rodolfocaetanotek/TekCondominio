package br.com.tekcondo.shared.io;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leitor de CSV tolerante às planilhas brasileiras: aceita UTF-8 (com ou sem BOM) e Windows-1252 (padrão do Excel
 * no Windows), separador ";" ou ",", campos entre aspas. Cabeçalhos são normalizados (minúsculas, sem acentos).
 */
public final class CsvReader {

    public record Row(int line, Map<String, String> values) {

        public String get(String column) {
            String v = values.get(column);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    private CsvReader() {
    }

    public static List<Row> read(byte[] bytes) {
        String text = decode(bytes);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        List<String> lines = text.lines().toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        char separator = lines.get(0).contains(";") ? ';' : ',';
        List<String> header = split(lines.get(0), separator).stream().map(CsvReader::normalizeHeader).toList();
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.replace(String.valueOf(separator), "").isBlank()) {
                continue;
            }
            List<String> cells = split(line, separator);
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                values.put(header.get(c), c < cells.size() ? cells.get(c) : null);
            }
            rows.add(new Row(i + 1, values));
        }
        return rows;
    }

    public static List<String> header(byte[] bytes) {
        String text = decode(bytes).replace("﻿", "");
        String first = text.lines().findFirst().orElse("");
        return split(first, first.contains(";") ? ';' : ',').stream().map(CsvReader::normalizeHeader).toList();
    }

    public static String normalizeHeader(String raw) {
        String noAccents = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private static List<String> split(String line, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == separator && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}
