package br.com.tekcondo.finance.service;

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
 * no máximo {@code toleranceDays} dias, e a linha só tem esse candidato e o candidato só tem essa linha. Os demais
 * casos ficam pendentes, com sugestões, para decisão humana.
 */
public final class StatementMatcher {

    public static final int DEFAULT_TOLERANCE_DAYS = 3;

    public record Line(UUID id, LocalDate postedOn, BigDecimal amount) {
    }

    /** Movimentação do sistema; {@code type}: PAYMENT, TRANSFER_IN ou TRANSFER_OUT. */
    public record Candidate(String type, UUID id, LocalDate date, BigDecimal amount, String description) {

        public String key() {
            return type + ":" + id;
        }
    }

    private StatementMatcher() {
    }

    public static List<Candidate> suggestions(Line line, List<Candidate> candidates, int toleranceDays) {
        return candidates.stream().filter(c -> compatible(line, c, toleranceDays))
                .sorted(Comparator.comparingLong(c -> distance(line, c))).toList();
    }

    /** Pares inequívocos: id da linha → candidato. */
    public static Map<UUID, Candidate> autoMatch(List<Line> lines, List<Candidate> candidates, int toleranceDays) {
        Map<UUID, List<Candidate>> byLine = new HashMap<>();
        Map<String, Integer> linesPerCandidate = new HashMap<>();
        for (Line line : lines) {
            List<Candidate> options = suggestions(line, candidates, toleranceDays);
            byLine.put(line.id(), options);
            options.forEach(c -> linesPerCandidate.merge(c.key(), 1, Integer::sum));
        }
        Map<UUID, Candidate> result = new HashMap<>();
        Set<String> taken = new HashSet<>();
        for (Line line : lines) {
            List<Candidate> options = byLine.get(line.id());
            if (options.size() == 1) {
                Candidate only = options.getFirst();
                if (linesPerCandidate.get(only.key()) == 1 && taken.add(only.key())) {
                    result.put(line.id(), only);
                }
            }
        }
        return result;
    }

    public static boolean compatible(Line line, Candidate c, int toleranceDays) {
        return line.amount().compareTo(c.amount()) == 0 && distance(line, c) <= toleranceDays;
    }

    private static long distance(Line line, Candidate c) {
        return Math.abs(ChronoUnit.DAYS.between(line.postedOn(), c.date()));
    }
}
