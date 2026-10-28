package br.com.tekcondo.billing.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.billing.entity.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    List<Allocation> findByCondominiumIdOrderByCompetenceDescCreatedAtDesc(UUID condominiumId);

    Optional<Allocation> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("""
            select count(a) > 0 from Allocation a where a.condominiumId = :condominiumId and a.competence = :competence
              and a.chartAccountId = :accountId and a.status = br.com.tekcondo.billing.entity.Allocation.Status.GENERATED
            """)
    boolean existsGenerated(UUID condominiumId, LocalDate competence, UUID accountId);
}
