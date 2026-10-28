package br.com.tekcondo.billing.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.billing.entity.CollectionNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionNoteRepository extends JpaRepository<CollectionNote, UUID> {

    List<CollectionNote> findByCondominiumIdAndUnitIdOrderByContactDateDescCreatedAtDesc(UUID condominiumId, UUID unitId);
}
