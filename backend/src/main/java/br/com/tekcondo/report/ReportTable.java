package br.com.tekcondo.report;

import java.util.List;

/**
 * Relatório tabular independente de formato: a mesma estrutura alimenta a tela (JSON) e as exportações
 * (CSV, Excel e PDF), garantindo que todos mostrem exatamente os mesmos números.
 */
public record ReportTable(String title, String subtitle, List<Column> columns, List<List<Object>> rows,
                          List<Object> totals, List<String> notes) {

    public enum Type {
        TEXT, MONEY, DATE, NUMBER, PERCENT
    }

    public record Column(String key, String label, Type type) {

        public static Column text(String key, String label) {
            return new Column(key, label, Type.TEXT);
        }

        public static Column money(String key, String label) {
            return new Column(key, label, Type.MONEY);
        }

        public static Column date(String key, String label) {
            return new Column(key, label, Type.DATE);
        }

        public static Column number(String key, String label) {
            return new Column(key, label, Type.NUMBER);
        }

        public static Column percent(String key, String label) {
            return new Column(key, label, Type.PERCENT);
        }
    }
}
