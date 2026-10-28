package br.com.tekcondo.finance.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.finance.entity.StatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StatementLineRepository extends JpaRepository<StatementLine, UUID> {

    @Query("select l.fitId from StatementLine l where l.bankAccountId = :accountId and l.fitId in :fitIds")
    Set<String> existingFitIds(UUID accountId, Collection<String> fitIds);

    @Query("""
            select l from StatementLine l where l.bankAccountId = :accountId
              and (:status is null or l.status = :status) order by l.postedOn desc, l.fitId
            """)
    List<StatementLine> findByAccount(UUID accountId, StatementLine.Status status);

    @Query("select l from StatementLine l where l.id = :id and l.condominiumId = :condominiumId")
    Optional<StatementLine> findInCondominium(UUID id, UUID condominiumId);

    Optional<StatementLine> findByPaymentId(UUID paymentId);

    Optional<StatementLine> findByBankAccountIdAndTransferId(UUID bankAccountId, UUID transferId);
}
