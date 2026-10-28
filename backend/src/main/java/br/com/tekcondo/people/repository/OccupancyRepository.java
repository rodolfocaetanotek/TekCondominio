package br.com.tekcondo.people.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.people.entity.Occupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OccupancyRepository extends JpaRepository<Occupancy, UUID> {

    @Query("""
            select o from Occupancy o join fetch o.person join fetch o.unit u left join fetch u.building
            where u.id = :unitId order by o.endDate nulls first, o.startDate desc
            """)
    List<Occupancy> findByUnit(UUID unitId);

    @Query("""
            select o from Occupancy o join fetch o.person join fetch o.unit u left join fetch u.building
            where o.person.id = :personId order by o.endDate nulls first, o.startDate desc
            """)
    List<Occupancy> findByPerson(UUID personId);

    @Query("""
            select o from Occupancy o join fetch o.person p
            where o.unit.id in :unitIds and o.endDate is null
            """)
    List<Occupancy> findCurrentByUnits(Collection<UUID> unitIds);

    @Query("select o from Occupancy o where o.unit.id = :unitId and o.financialResponsible = true and o.endDate is null")
    Optional<Occupancy> findCurrentFinancialResponsible(UUID unitId);

    @Query("""
            select o from Occupancy o join fetch o.unit u
            where o.person.id = :personId and o.endDate is null and u.condominiumId = :condominiumId
            """)
    List<Occupancy> findCurrentByPersonInCondominium(UUID personId, UUID condominiumId);

    @Query("""
            select count(o) > 0 from Occupancy o
            where o.unit.id = :unitId and o.person.id = :personId and o.role = :role and o.endDate is null
            """)
    boolean existsCurrent(UUID unitId, UUID personId, Occupancy.Role role);
}
