package br.com.tekcondo.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.finance.service.StatementMatcher.Candidate;
import br.com.tekcondo.finance.service.StatementMatcher.Line;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatementMatcherTest {

    static final LocalDate D = LocalDate.of(2026, 10, 10);

    static Line line(String amount, int dayOffset) {
        return new Line(UUID.randomUUID(), D.plusDays(dayOffset), new BigDecimal(amount));
    }

    static Candidate payment(String amount, int dayOffset) {
        return new Candidate("PAYMENT", UUID.randomUUID(), D.plusDays(dayOffset), new BigDecimal(amount), "x");
    }

    @Test
    void matchesWhenAmountAndDateWindowAreUnambiguous() {
        Line l = line("350.00", 0);
        Candidate c = payment("350.00", 2);
        assertThat(StatementMatcher.autoMatch(List.of(l), List.of(c, payment("350.00", 9), payment("-350.00", 0)), 3))
                .containsExactly(Map.entry(l.id(), c));
    }

    @Test
    void neverGuessesBetweenEquivalentCandidates() {
        Line l = line("350.00", 0);
        Candidate a = payment("350.00", 1);
        Candidate b = payment("350.00", -1);
        assertThat(StatementMatcher.autoMatch(List.of(l), List.of(a, b), 3)).isEmpty();
        assertThat(StatementMatcher.suggestions(l, List.of(a, b), 3)).hasSize(2);
    }

    @Test
    void candidateDisputedByTwoLinesIsLeftForManualDecision() {
        Line l1 = line("120.00", 0);
        Line l2 = line("120.00", 1);
        Candidate c = payment("120.00", 0);
        assertThat(StatementMatcher.autoMatch(List.of(l1, l2), List.of(c), 3)).isEmpty();
    }

    @Test
    void signMustAgree() {
        Line debit = line("-80.00", 0);
        assertThat(StatementMatcher.autoMatch(List.of(debit), List.of(payment("80.00", 0)), 3)).isEmpty();
    }
}
