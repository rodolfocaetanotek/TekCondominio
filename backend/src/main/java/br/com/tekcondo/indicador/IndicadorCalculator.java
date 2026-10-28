package br.com.tekcondo.indicador;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cálculos puros dos indicadores (sem acesso a banco), cobertos por testes unitários. */
public final class IndicadorCalculator {

    /** Valor em aberto com vencimento. {@code aReceber} = entrada prevista. */
    public record Vencimento(LocalDate dataVencimento, BigDecimal valor, boolean aReceber) {
    }

    public record Semana(LocalDate inicio, LocalDate fim, BigDecimal entradasPrevistas, BigDecimal saidasPrevistas,
                         BigDecimal saldoProjetado) {
    }

    /** Resultado da comparação de um mês com o histórico da conta. */
    public record Desvio(BigDecimal valor, BigDecimal media, BigDecimal desvioPadrao, BigDecimal razao) {
    }

    private IndicadorCalculator() {
    }

    /**
     * Projeção semanal a partir de {@code hoje}: apenas títulos com vencimento a partir de hoje entram nas semanas —
     * os vencidos não são presumidos como recebidos/pagos (o chamador os informa à parte).
     */
    public static List<Semana> projetar(BigDecimal saldoAtual, List<Vencimento> vencimentos, LocalDate hoje, int semanas) {
        List<Semana> resultado = new ArrayList<>();
        BigDecimal saldo = saldoAtual;
        for (int w = 0; w < semanas; w++) {
            LocalDate inicio = hoje.plusWeeks(w);
            LocalDate fim = inicio.plusDays(6);
            BigDecimal entradas = BigDecimal.ZERO;
            BigDecimal saidas = BigDecimal.ZERO;
            for (Vencimento v : vencimentos) {
                if (!v.dataVencimento().isBefore(inicio) && !v.dataVencimento().isAfter(fim)) {
                    if (v.aReceber()) entradas = entradas.add(v.valor());
                    else saidas = saidas.add(v.valor());
                }
            }
            saldo = saldo.add(entradas).subtract(saidas);
            resultado.add(new Semana(inicio, fim, entradas, saidas, saldo));
        }
        return resultado;
    }

    /**
     * Despesa fora do padrão: exige ao menos {@code minimoHistorico} meses com movimento no histórico e que o valor do
     * mês supere, ao mesmo tempo, média + 2 desvios-padrão, 130% da média e a média + {@code minimoAbsoluto}.
     * Devolve nulo quando não há desvio (ou histórico insuficiente para afirmar).
     */
    public static Desvio desvio(BigDecimal valor, List<BigDecimal> historico, int minimoHistorico, BigDecimal minimoAbsoluto) {
        long comDados = historico.stream().filter(v -> v.signum() > 0).count();
        if (comDados < minimoHistorico || valor.signum() <= 0) {
            return null;
        }
        MathContext mc = MathContext.DECIMAL64;
        BigDecimal n = BigDecimal.valueOf(historico.size());
        BigDecimal media = historico.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(n, mc);
        BigDecimal variancia = historico.stream().map(v -> v.subtract(media).pow(2, mc)).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(n, mc);
        BigDecimal desvioPadrao = variancia.sqrt(mc);
        boolean foraDoPadrao = valor.compareTo(media.add(desvioPadrao.multiply(BigDecimal.TWO))) > 0
                && valor.compareTo(media.multiply(new BigDecimal("1.3"))) > 0
                && valor.subtract(media).compareTo(minimoAbsoluto) > 0;
        if (!foraDoPadrao) {
            return null;
        }
        BigDecimal razao = media.signum() == 0 ? null : valor.divide(media, 2, RoundingMode.HALF_UP);
        return new Desvio(valor, media.setScale(2, RoundingMode.HALF_UP), desvioPadrao.setScale(2, RoundingMode.HALF_UP), razao);
    }
}
