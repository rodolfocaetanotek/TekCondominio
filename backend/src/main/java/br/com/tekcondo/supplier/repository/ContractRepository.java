package br.com.tekcondo.supplier.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.supplier.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    @Query("select c from Contract c join fetch c.supplier where c.condominiumId = :condominiumId order by c.status, c.endDate nulls last")
    List<Contract> findByCondominium(UUID condominiumId);

    Optional<Contract> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("select c from Contract c join fetch c.supplier where c.status = br.com.tekcondo.supplier.entity.Contract.Status.ACTIVE and c.endDate is not null")
    List<Contract> findActiveWithEndDate();
}
