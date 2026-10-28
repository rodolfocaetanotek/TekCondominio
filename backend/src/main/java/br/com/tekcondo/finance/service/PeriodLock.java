package br.com.tekcondo.finance.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Bloqueio de período fechado. Toda operação que altera saldo, conciliação ou competência chama
 * {@link #ensureOpen}, que trava a linha do condomínio em modo compartilhado até o fim da transação: um
 * fechamento concorrente (que trava em modo exclusivo) espera a operação terminar — e vice-versa.
 */
@Component
public class PeriodLock {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;

    public PeriodLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDate closedUntil(UUID condominiumId) {
        return jdbc.queryForObject("select books_closed_until from condominium where id = ?", LocalDate.class, condominiumId);
    }

    /** Trava o condomínio para fechamento/reabertura (modo exclusivo) e devolve a data fechada atual. */
    LocalDate lockForClosing(UUID condominiumId) {
        return jdbc.queryForObject("select books_closed_until from condominium where id = ? for update", LocalDate.class, condominiumId);
    }

    void setClosedUntil(UUID condominiumId, LocalDate until) {
        jdbc.update("update condominium set books_closed_until = ? where id = ?", until, condominiumId);
    }

    /** Lança 422 se alguma das datas estiver no período fechado. Datas nulas são ignoradas. */
    public void ensureOpen(UUID condominiumId, LocalDate... dates) {
        LocalDate closed = jdbc.queryForObject("select books_closed_until from condominium where id = ? for share",
                LocalDate.class, condominiumId);
        if (closed == null) {
            return;
        }
        for (LocalDate date : dates) {
            if (date != null && !date.isAfter(closed)) {
                throw ApiException.regraNegocio("PERIOD_CLOSED", "O período financeiro até " + closed.format(BR)
                        + " está fechado. Operações com data ou competência até essa data exigem a reabertura do período.");
            }
        }
    }
}
