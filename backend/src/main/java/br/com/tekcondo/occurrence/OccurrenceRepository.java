package br.com.tekcondo.occurrence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface OccurrenceRepository extends JpaRepository<Occurrence, UUID>, JpaSpecificationExecutor<Occurrence> {

    Optional<Occurrence> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("select coalesce(max(o.number), 0) + 1 from Occurrence o where o.condominiumId = :condominiumId")
    int nextNumber(UUID condominiumId);
}
