package br.com.tekcondo.finance.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.finance.entity.BankTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BankTransferRepository extends JpaRepository<BankTransfer, UUID> {

    @Query("""
            select t from BankTransfer t join fetch t.fromAccount join fetch t.toAccount
            where t.condominiumId = :condominiumId order by t.transferDate desc, t.createdAt desc
            """)
    List<BankTransfer> findByCondominium(UUID condominiumId);

    Optional<BankTransfer> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("select t from BankTransfer t where t.id in :ids and t.condominiumId = :condominiumId")
    List<BankTransfer> findAllInCondominium(Collection<UUID> ids, UUID condominiumId);
}
