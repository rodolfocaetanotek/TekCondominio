package br.com.tekcondo.structure.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.structure.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UnitRepository extends JpaRepository<Unit, UUID>, JpaSpecificationExecutor<Unit> {

    Optional<Unit> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    Optional<Unit> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select u from Unit u left join fetch u.building where u.condominiumId = :condominiumId and lower(u.identifier) = lower(:identifier)")
    List<Unit> findByIdentifier(UUID condominiumId, String identifier);

    @Query("select coalesce(sum(u.idealFraction), 0) from Unit u where u.condominiumId = :condominiumId and u.active = true")
    BigDecimal sumActiveFractions(UUID condominiumId);

    long countByCondominiumIdAndActiveTrue(UUID condominiumId);

    @Query("select u from Unit u left join fetch u.building where u.condominiumId = :condominiumId and u.active = true order by u.identifier")
    List<Unit> findActiveByCondominium(UUID condominiumId);
}
