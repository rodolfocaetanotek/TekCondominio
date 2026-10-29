package br.com.tekcondo.reserva;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    Optional<Reserva> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("""
            select count(r) > 0 from Reserva r where r.idArea = :idArea and r.data = :data
              and r.situacao in (br.com.tekcondo.reserva.Reserva.Situacao.PENDING, br.com.tekcondo.reserva.Reserva.Situacao.CONFIRMED)
              and r.horaInicio < :fim and r.horaFim > :inicio
            """)
    boolean haSobreposicao(UUID idArea, LocalDate data, LocalTime inicio, LocalTime fim);

    @Query("""
            select count(r) from Reserva r where r.idArea = :idArea and r.idUnidade = :idUnidade and r.data between :de and :ate
              and r.situacao in (br.com.tekcondo.reserva.Reserva.Situacao.PENDING, br.com.tekcondo.reserva.Reserva.Situacao.CONFIRMED)
            """)
    long contarAtivasDaUnidade(UUID idArea, UUID idUnidade, LocalDate de, LocalDate ate);

    @Query("""
            select r from Reserva r where r.idCondominio = :idCondominio and r.data between :de and :ate
              and (:idArea is null or r.idArea = :idArea) order by r.data, r.horaInicio
            """)
    List<Reserva> buscarNoPeriodo(UUID idCondominio, UUID idArea, LocalDate de, LocalDate ate);

    @Query("select r from Reserva r where r.idCondominio = :idCondominio and r.idUnidade in :idsUnidades order by r.data desc, r.horaInicio")
    List<Reserva> buscarDasUnidades(UUID idCondominio, Collection<UUID> idsUnidades);
}
