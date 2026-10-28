package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.financeiro.service.ConciliacaoExtratoMatcher.Candidato;
import br.com.tekcondo.financeiro.service.ConciliacaoExtratoMatcher.Linha;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConciliacaoExtratoMatcherTest {

    static final LocalDate D = LocalDate.of(2026, 10, 10);

    static Linha linha(String valor, int deslocamentoDias) {
        return new Linha(UUID.randomUUID(), D.plusDays(deslocamentoDias), new BigDecimal(valor));
    }

    static Candidato baixa(String valor, int deslocamentoDias) {
        return new Candidato("PAYMENT", UUID.randomUUID(), D.plusDays(deslocamentoDias), new BigDecimal(valor), "x");
    }

    @Test
    void casaQuandoValorEJanelaDeDatasSaoInequivocos() {
        Linha l = linha("350.00", 0);
        Candidato c = baixa("350.00", 2);
        assertThat(ConciliacaoExtratoMatcher.paresInequivocos(List.of(l), List.of(c, baixa("350.00", 9), baixa("-350.00", 0)), 3))
                .containsExactly(Map.entry(l.id(), c));
    }

    @Test
    void nuncaAdivinhaEntreCandidatosEquivalentes() {
        Linha l = linha("350.00", 0);
        Candidato a = baixa("350.00", 1);
        Candidato b = baixa("350.00", -1);
        assertThat(ConciliacaoExtratoMatcher.paresInequivocos(List.of(l), List.of(a, b), 3)).isEmpty();
        assertThat(ConciliacaoExtratoMatcher.sugestoes(l, List.of(a, b), 3)).hasSize(2);
    }

    @Test
    void candidatoDisputadoPorDuasLinhasFicaParaDecisaoManual() {
        Linha l1 = linha("120.00", 0);
        Linha l2 = linha("120.00", 1);
        Candidato c = baixa("120.00", 0);
        assertThat(ConciliacaoExtratoMatcher.paresInequivocos(List.of(l1, l2), List.of(c), 3)).isEmpty();
    }

    @Test
    void sinalPrecisaConferir() {
        Linha debito = linha("-80.00", 0);
        assertThat(ConciliacaoExtratoMatcher.paresInequivocos(List.of(debito), List.of(baixa("80.00", 0)), 3)).isEmpty();
    }
}
