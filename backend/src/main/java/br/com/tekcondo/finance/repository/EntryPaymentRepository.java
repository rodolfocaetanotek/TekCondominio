package br.com.tekcondo.finance.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.finance.entity.EntryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryPaymentRepository extends JpaRepository<EntryPayment, UUID> {

    @Query("select p from EntryPayment p join fetch p.bankAccount where p.entry.id = :entryId order by p.paidOn, p.createdAt")
    List<EntryPayment> findByEntry(UUID entryId);

    @Query("select p from EntryPayment p join fetch p.entry where p.id = :id and p.condominiumId = :condominiumId")
    Optional<EntryPayment> findInCondominium(UUID id, UUID condominiumId);

    @Query("select p from EntryPayment p where p.id in :ids and p.condominiumId = :condominiumId")
    List<EntryPayment> findAllInCondominium(Collection<UUID> ids, UUID condominiumId);
}
