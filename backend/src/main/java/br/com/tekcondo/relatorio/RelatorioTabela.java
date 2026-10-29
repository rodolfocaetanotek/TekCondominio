package br.com.tekcondo.relatorio;

import java.util.List;

/**
 * Relatório tabular independente de formato: a mesma estrutura alimenta a tela (JSON) e as exportações
 * (CSV, Excel e PDF), garantindo que todos mostrem exatamente os mesmos números.
 */
public record RelatorioTabela(String titulo, String subtitulo, List<Coluna> colunas, List<List<Object>> linhas,
                              List<Object> totais, List<String> observacoes) {

    public enum Tipo {
        TEXT, MONEY, DATE, NUMBER, PERCENT
    }

    public record Coluna(String chave, String rotulo, Tipo tipo) {

        public static Coluna texto(String chave, String rotulo) {
            return new Coluna(chave, rotulo, Tipo.TEXT);
        }

        public static Coluna moeda(String chave, String rotulo) {
            return new Coluna(chave, rotulo, Tipo.MONEY);
        }

        public static Coluna data(String chave, String rotulo) {
            return new Coluna(chave, rotulo, Tipo.DATE);
        }

        public static Coluna numero(String chave, String rotulo) {
            return new Coluna(chave, rotulo, Tipo.NUMBER);
        }

        public static Coluna percentual(String chave, String rotulo) {
            return new Coluna(chave, rotulo, Tipo.PERCENT);
        }
    }
}
