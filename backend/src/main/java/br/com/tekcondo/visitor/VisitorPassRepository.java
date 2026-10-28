package br.com.tekcondo.visitor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VisitorPassRepository extends JpaRepository<VisitorPass, UUID> {

    Optional<VisitorPass> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("""
            select v from VisitorPass v where v.condominiumId = :condominiumId and v.accessCode = :code
              and v.status = br.com.tekcondo.visitor.VisitorPass.Status.EXPECTED
            """)
    Optional<VisitorPass> findExpectedByCode(UUID condominiumId, String code);

    /** Portaria do dia: esperados válidos hoje, quem está dentro e o que entrou/saiu hoje. */
    @Query("""
            select v from VisitorPass v where v.condominiumId = :condominiumId and (
                (v.status = br.com.tekcondo.visitor.VisitorPass.Status.EXPECTED and :day between v.validFrom and v.validUntil)
                or v.status = br.com.tekcondo.visitor.VisitorPass.Status.INSIDE
                or (v.enteredAt >= :dayStart and v.enteredAt < :dayEnd))
            order by v.status, v.name
            """)
    List<VisitorPass> findForDay(UUID condominiumId, LocalDate day, Instant dayStart, Instant dayEnd);

    @Query("""
            select v from VisitorPass v where v.condominiumId = :condominiumId and v.createdAt >= :from and v.createdAt < :to
            order by v.createdAt desc
            """)
    List<VisitorPass> findCreatedBetween(UUID condominiumId, Instant from, Instant to);

    @Query("select v from VisitorPass v where v.condominiumId = :condominiumId and v.unitId in :unitIds order by v.validFrom desc, v.createdAt desc")
    List<VisitorPass> findByUnits(UUID condominiumId, Collection<UUID> unitIds);

    /** Registros encerrados há mais tempo que a retenção e ainda não anonimizados. */
    @Query("""
            select v from VisitorPass v where v.anonymizedAt is null and v.validUntil < :before
              and v.status <> br.com.tekcondo.visitor.VisitorPass.Status.INSIDE
            """)
    List<VisitorPass> findToAnonymize(LocalDate before);
}
