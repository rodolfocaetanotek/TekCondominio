package br.com.tekcondo.integracao.banco;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import br.com.tekcondo.comum.exception.ApiException;

/**
 * Leitor do arquivo de retorno de cobrança CNAB 240 no layout padrão FEBRABAN (segmentos T e U).
 * Posições conforme o layout FEBRABAN v10; cada banco pode ter particularidades (ex.: composição do nosso número),
 * por isso o uso em produção exige homologação com o banco do condomínio — ver docs/INTEGRACOES.md.
 */
public final class RetornoCnab240Parser {

    /** Movimentos de liquidação (título pago). */
    public static final List<String> CODIGOS_LIQUIDACAO = List.of("06", "17");

    public record Item(int sequencia, String codigoMovimento, String nossoNumero, String seuNumero, LocalDate dataVencimento,
                       BigDecimal valorTitulo, BigDecimal encargos, BigDecimal desconto, BigDecimal valorPago,
                       BigDecimal valorLiquido, BigDecimal tarifa, LocalDate dataOcorrencia, LocalDate dataCredito) {

        public boolean isLiquidacao() {
            return CODIGOS_LIQUIDACAO.contains(codigoMovimento);
        }
    }

    public record Retorno(String codigoBanco, LocalDate dataGeracao, List<Item> itens) {
    }

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMyyyy");

    private RetornoCnab240Parser() {
    }

    public static Retorno parse(byte[] conteudo) {
        String[] linhas = new String(conteudo, StandardCharsets.ISO_8859_1).split("\\r?\\n");
        String banco = null;
        LocalDate geracao = null;
        List<Item> itens = new ArrayList<>();
        String[] segmentoTPendente = null;
        int numeroLinha = 0;
        for (String bruto : linhas) {
            numeroLinha++;
            if (bruto.isBlank()) {
                continue;
            }
            if (bruto.length() < 240) {
                throw invalido("linha " + numeroLinha + " com " + bruto.length() + " posições (esperado 240)");
            }
            String linha = bruto.substring(0, 240);
            char tipoRegistro = linha.charAt(7);
            if (tipoRegistro == '0') {
                banco = linha.substring(0, 3);
                geracao = data(linha, 143, numeroLinha);
            } else if (tipoRegistro == '3') {
                char segmento = linha.charAt(13);
                if (segmento == 'T') {
                    segmentoTPendente = new String[]{linha, String.valueOf(numeroLinha)};
                } else if (segmento == 'U') {
                    if (segmentoTPendente == null) {
                        throw invalido("segmento U sem o segmento T correspondente na linha " + numeroLinha);
                    }
                    itens.add(item(segmentoTPendente[0], linha, numeroLinha));
                    segmentoTPendente = null;
                }
            }
        }
        if (banco == null) {
            throw invalido("header de arquivo (registro 0) ausente");
        }
        return new Retorno(banco, geracao, itens);
    }

    private static Item item(String t, String u, int numeroLinha) {
        return new Item(Integer.parseInt(t.substring(8, 13).trim().isEmpty() ? "0" : t.substring(8, 13).trim()),
                t.substring(15, 17), t.substring(37, 57).trim(), t.substring(58, 73).trim(), data(t, 73, numeroLinha),
                valor(t, 81, 96), valor(u, 17, 32), valor(u, 32, 47).add(valor(u, 47, 62)), valor(u, 77, 92),
                valor(u, 92, 107), valor(t, 198, 213), data(u, 137, numeroLinha), data(u, 145, numeroLinha));
    }

    private static BigDecimal valor(String linha, int de, int ate) {
        String digitos = linha.substring(de, ate).trim();
        if (digitos.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (!digitos.chars().allMatch(Character::isDigit)) {
            throw invalido("valor não numérico nas posições " + (de + 1) + "-" + ate);
        }
        return new BigDecimal(digitos).movePointLeft(2);
    }

    private static LocalDate data(String linha, int de, int numeroLinha) {
        String v = linha.substring(de, de + 8).trim();
        if (v.isEmpty() || v.chars().allMatch(c -> c == '0')) {
            return null;
        }
        try {
            return LocalDate.parse(v, DDMMAAAA);
        } catch (RuntimeException e) {
            throw invalido("data inválida \"" + v + "\" na linha " + numeroLinha);
        }
    }

    private static ApiException invalido(String detalhe) {
        return ApiException.requisicaoInvalida("INVALID_CNAB", "Arquivo de retorno CNAB 240 inválido: " + detalhe + ".");
    }
}
