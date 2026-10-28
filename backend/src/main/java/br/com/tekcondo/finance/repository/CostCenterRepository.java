package br.com.tekcondo.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.finance.entity.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CostCenterRepository extends JpaRepository<CostCenter, UUID> {

    List<CostCenter> findByCondominiumIdOrderByNameAsc(UUID condominiumId);

    Optional<CostCenter> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("select c from CostCenter c where c.condominiumId = :condominiumId and lower(c.name) = lower(:name)")
    Optional<CostCenter> findByName(UUID condominiumId, String name);
}
