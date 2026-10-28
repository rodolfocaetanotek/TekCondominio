package br.com.tekcondo.structure.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.structure.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BuildingRepository extends JpaRepository<Building, UUID> {

    List<Building> findByCondominiumIdOrderByNameAsc(UUID condominiumId);

    Optional<Building> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("select b from Building b where b.condominiumId = :condominiumId and lower(b.name) = lower(:name)")
    Optional<Building> findByName(UUID condominiumId, String name);

    @Query("select count(u) from Unit u where u.building.id = :buildingId and u.active = true")
    long countActiveUnits(UUID buildingId);
}
