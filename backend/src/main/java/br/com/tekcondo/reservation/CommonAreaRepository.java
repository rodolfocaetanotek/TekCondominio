package br.com.tekcondo.reservation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CommonAreaRepository extends JpaRepository<CommonArea, UUID> {

    List<CommonArea> findByCondominiumIdOrderByNameAsc(UUID condominiumId);

    Optional<CommonArea> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("select count(a) > 0 from CommonArea a where a.condominiumId = :condominiumId and lower(a.name) = lower(:name) and a.id <> :excludeId")
    boolean nameTaken(UUID condominiumId, String name, UUID excludeId);

    /** Trava a área até o fim da transação: serializa reservas concorrentes da mesma área. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CommonArea a where a.id = :id and a.condominiumId = :condominiumId")
    Optional<CommonArea> lockInCondominium(UUID id, UUID condominiumId);
}
