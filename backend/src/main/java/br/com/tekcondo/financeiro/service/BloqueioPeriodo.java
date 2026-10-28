package br.com.tekcondo.financeiro.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Bloqueio de período fechado. Toda operação que altera saldo, conciliação ou competência chama
 * {@link #exigirAberto}, que trava a linha do condomínio em modo compartilhado até o fim da transação: um
 * fechamento concorrente (que trava em modo exclusivo) espera a operação terminar — e vice-versa.
 */
@Component
public class BloqueioPeriodo {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;

    public BloqueioPeriodo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDate fechadoAte(UUID idCondominio) {
        return jdbc.queryForObject("select books_closed_until from condominium where id = ?", LocalDate.class, idCondominio);
    }

    /** Trava o condomínio para fechamento/reabertura (modo exclusivo) e devolve a data fechada atual. */
    LocalDate travarParaFechamento(UUID idCondominio) {
        return jdbc.queryForObject("select books_closed_until from condominium where id = ? for update", LocalDate.class, idCondominio);
    }

    void definirFechadoAte(UUID idCondominio, LocalDate ate) {
        jdbc.update("update condominium set books_closed_until = ? where id = ?", ate, idCondominio);
    }

    /** Lança 422 se alguma das datas estiver no período fechado. Datas nulas são ignoradas. */
    public void exigirAberto(UUID idCondominio, LocalDate... datas) {
        LocalDate fechado = jdbc.queryForObject("select books_closed_until from condominium where id = ? for share",
                LocalDate.class, idCondominio);
        if (fechado == null) {
            return;
        }
        for (LocalDate data : datas) {
            if (data != null && !data.isAfter(fechado)) {
                throw ApiException.regraNegocio("PERIOD_CLOSED", "O período financeiro até " + fechado.format(BR)
                        + " está fechado. Operações com data ou competência até essa data exigem a reabertura do período.");
            }
        }
    }
}
