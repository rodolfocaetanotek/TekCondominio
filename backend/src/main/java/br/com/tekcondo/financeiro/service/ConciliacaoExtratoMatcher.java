package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Casamento entre linhas do extrato do banco e movimentações registradas no sistema (baixas e transferências).
 * Regra conservadora: casa automaticamente apenas quando o par é <b>inequívoco</b> — mesmo valor com sinal, datas a
 * no máximo {@code toleranciaDias} dias, e a linha só tem esse candidato e o candidato só tem essa linha. Os demais
 * casos ficam pendentes, com sugestões, para decisão humana.
 */
public final class ConciliacaoExtratoMatcher {

    public static final int TOLERANCIA_PADRAO_DIAS = 3;

    public record Linha(UUID id, LocalDate dataLancamento, BigDecimal valor) {
    }

    /** Movimentação do sistema; {@code tipo}: PAYMENT, TRANSFER_IN ou TRANSFER_OUT. */
    public record Candidato(String tipo, UUID id, LocalDate data, BigDecimal valor, String descricao) {

        public String chave() {
            return tipo + ":" + id;
        }
    }

    private ConciliacaoExtratoMatcher() {
    }

    public static List<Candidato> sugestoes(Linha linha, List<Candidato> candidatos, int toleranciaDias) {
        return candidatos.stream().filter(c -> compativel(linha, c, toleranciaDias))
                .sorted(Comparator.comparingLong(c -> distancia(linha, c))).toList();
    }

    /** Pares inequívocos: id da linha → candidato. */
    public static Map<UUID, Candidato> paresInequivocos(List<Linha> linhas, List<Candidato> candidatos, int toleranciaDias) {
        Map<UUID, List<Candidato>> porLinha = new HashMap<>();
        Map<String, Integer> linhasPorCandidato = new HashMap<>();
        for (Linha linha : linhas) {
            List<Candidato> opcoes = sugestoes(linha, candidatos, toleranciaDias);
            porLinha.put(linha.id(), opcoes);
            opcoes.forEach(c -> linhasPorCandidato.merge(c.chave(), 1, Integer::sum));
        }
        Map<UUID, Candidato> resultado = new HashMap<>();
        Set<String> usados = new HashSet<>();
        for (Linha linha : linhas) {
            List<Candidato> opcoes = porLinha.get(linha.id());
            if (opcoes.size() == 1) {
                Candidato unico = opcoes.getFirst();
                if (linhasPorCandidato.get(unico.chave()) == 1 && usados.add(unico.chave())) {
                    resultado.put(linha.id(), unico);
                }
            }
        }
        return resultado;
    }

    public static boolean compativel(Linha linha, Candidato c, int toleranciaDias) {
        return linha.valor().compareTo(c.valor()) == 0 && distancia(linha, c) <= toleranciaDias;
    }

    private static long distancia(Linha linha, Candidato c) {
        return Math.abs(ChronoUnit.DAYS.between(linha.dataLancamento(), c.data()));
    }
}
