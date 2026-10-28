package br.com.tekcondo.comum.io;

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

    public record Linha(int numero, Map<String, String> valores) {

        public String get(String coluna) {
            String v = valores.get(coluna);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    private CsvReader() {
    }

    public static List<Linha> ler(byte[] bytes) {
        String texto = decodificar(bytes);
        if (!texto.isEmpty() && texto.charAt(0) == '﻿') {
            texto = texto.substring(1);
        }
        List<String> linhas = texto.lines().toList();
        if (linhas.isEmpty()) {
            return List.of();
        }
        char separador = linhas.get(0).contains(";") ? ';' : ',';
        List<String> cabecalho = dividir(linhas.get(0), separador).stream().map(CsvReader::normalizarCabecalho).toList();
        List<Linha> resultado = new ArrayList<>();
        for (int i = 1; i < linhas.size(); i++) {
            String linha = linhas.get(i);
            if (linha.isBlank() || linha.replace(String.valueOf(separador), "").isBlank()) {
                continue;
            }
            List<String> celulas = dividir(linha, separador);
            Map<String, String> valores = new LinkedHashMap<>();
            for (int c = 0; c < cabecalho.size(); c++) {
                valores.put(cabecalho.get(c), c < celulas.size() ? celulas.get(c) : null);
            }
            resultado.add(new Linha(i + 1, valores));
        }
        return resultado;
    }

    public static List<String> cabecalho(byte[] bytes) {
        String texto = decodificar(bytes).replace("﻿", "");
        String primeira = texto.lines().findFirst().orElse("");
        return dividir(primeira, primeira.contains(";") ? ';' : ',').stream().map(CsvReader::normalizarCabecalho).toList();
    }

    public static String normalizarCabecalho(String bruto) {
        String semAcentos = Normalizer.normalize(bruto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String decodificar(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private static List<String> dividir(String linha, char separador) {
        List<String> celulas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char ch = linha.charAt(i);
            if (ch == '"') {
                if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (ch == separador && !entreAspas) {
                celulas.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(ch);
            }
        }
        celulas.add(atual.toString());
        return celulas;
    }
}
