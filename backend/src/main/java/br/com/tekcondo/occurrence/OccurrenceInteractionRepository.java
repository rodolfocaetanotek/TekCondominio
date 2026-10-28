package br.com.tekcondo.occurrence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OccurrenceInteractionRepository extends JpaRepository<OccurrenceInteraction, UUID> {

    List<OccurrenceInteraction> findByOccurrenceIdOrderByCreatedAtAsc(UUID occurrenceId);
}
