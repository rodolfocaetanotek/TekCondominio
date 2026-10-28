package br.com.tekcondo.reservation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("""
            select count(r) > 0 from Reservation r where r.areaId = :areaId and r.date = :date
              and r.status in (br.com.tekcondo.reservation.Reservation.Status.PENDING, br.com.tekcondo.reservation.Reservation.Status.CONFIRMED)
              and r.startTime < :end and r.endTime > :start
            """)
    boolean overlaps(UUID areaId, LocalDate date, LocalTime start, LocalTime end);

    @Query("""
            select count(r) from Reservation r where r.areaId = :areaId and r.unitId = :unitId and r.date between :from and :to
              and r.status in (br.com.tekcondo.reservation.Reservation.Status.PENDING, br.com.tekcondo.reservation.Reservation.Status.CONFIRMED)
            """)
    long countActiveForUnit(UUID areaId, UUID unitId, LocalDate from, LocalDate to);

    @Query("""
            select r from Reservation r where r.condominiumId = :condominiumId and r.date between :from and :to
              and (:areaId is null or r.areaId = :areaId) order by r.date, r.startTime
            """)
    List<Reservation> findInPeriod(UUID condominiumId, UUID areaId, LocalDate from, LocalDate to);

    @Query("select r from Reservation r where r.condominiumId = :condominiumId and r.unitId in :unitIds order by r.date desc, r.startTime")
    List<Reservation> findByUnits(UUID condominiumId, Collection<UUID> unitIds);
}
