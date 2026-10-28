package br.com.tekcondo.assembly;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.assembly.AssemblyEntities.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    List<Vote> findByAssemblyId(UUID assemblyId);

    boolean existsByAgendaItemIdAndUnitId(UUID agendaItemId, UUID unitId);

    boolean existsByAssemblyIdAndUnitId(UUID assemblyId, UUID unitId);
}
