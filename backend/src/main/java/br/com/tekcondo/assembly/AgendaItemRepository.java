package br.com.tekcondo.assembly;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.assembly.AssemblyEntities.AgendaItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, UUID> {

    List<AgendaItem> findByAssemblyIdOrderByPositionAsc(UUID assemblyId);

    void deleteByAssemblyId(UUID assemblyId);
}
