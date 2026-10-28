package br.com.tekcondo.billing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.billing.entity.BankReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BankReturnItemRepository extends JpaRepository<BankReturnItem, UUID> {

    List<BankReturnItem> findByFileIdOrderBySequenceAsc(UUID fileId);

    @Query("select i from BankReturnItem i where i.condominiumId = :condominiumId and i.status = :status order by i.occurrenceDate desc")
    List<BankReturnItem> findByStatus(UUID condominiumId, BankReturnItem.Status status);

    @Query("select i from BankReturnItem i where i.id = :id and i.condominiumId = :condominiumId")
    Optional<BankReturnItem> findInCondominium(UUID id, UUID condominiumId);

    @Query("""
            select count(i) > 0 from BankReturnItem i where i.bankAccountId = :accountId and i.status = 'SETTLED'
              and i.movementCode = :movement and i.occurrenceDate = :occurred
              and coalesce(i.ourNumber, '') = coalesce(:ourNumber, '') and coalesce(i.yourNumber, '') = coalesce(:yourNumber, '')
            """)
    boolean alreadySettled(UUID accountId, String movement, java.time.LocalDate occurred, String ourNumber, String yourNumber);
}
