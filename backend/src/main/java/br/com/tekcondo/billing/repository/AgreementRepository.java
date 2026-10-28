package br.com.tekcondo.billing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.billing.entity.Agreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AgreementRepository extends JpaRepository<Agreement, UUID> {

    List<Agreement> findByCondominiumIdOrderByCreatedAtDesc(UUID condominiumId);

    Optional<Agreement> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Modifying
    @Query(value = "insert into agreement_original_entry (agreement_id, entry_id) values (:agreementId, :entryId)", nativeQuery = true)
    void linkOriginal(UUID agreementId, UUID entryId);

    @Query(value = "select entry_id from agreement_original_entry where agreement_id = :agreementId", nativeQuery = true)
    List<UUID> originalEntryIds(UUID agreementId);
}
