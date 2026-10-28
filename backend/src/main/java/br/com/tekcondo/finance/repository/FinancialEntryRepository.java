package br.com.tekcondo.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.finance.entity.FinancialEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface FinancialEntryRepository extends JpaRepository<FinancialEntry, UUID>, JpaSpecificationExecutor<FinancialEntry> {

    Optional<FinancialEntry> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("""
            select count(e) > 0 from FinancialEntry e
            where e.condominiumId = :condominiumId and e.supplier.id = :supplierId and e.documentNumber = :documentNumber
              and e.status <> br.com.tekcondo.finance.entity.FinancialEntry.Status.CANCELLED and e.id <> :excludeId
            """)
    boolean existsSupplierDocument(UUID condominiumId, UUID supplierId, String documentNumber, UUID excludeId);

    List<FinancialEntry> findByAllocationId(UUID allocationId);

    List<FinancialEntry> findByAgreementId(UUID agreementId);

    @Query("""
            select e from FinancialEntry e left join fetch e.unit u left join fetch u.building
            where e.condominiumId = :condominiumId and e.kind = br.com.tekcondo.finance.entity.FinancialEntry.Kind.INCOME
              and e.unit.id = :unitId order by e.dueDate
            """)
    List<FinancialEntry> findReceivablesOfUnit(UUID condominiumId, UUID unitId);

    Optional<FinancialEntry> findByCondominiumIdAndCollectionNumber(UUID condominiumId, Long collectionNumber);
}
