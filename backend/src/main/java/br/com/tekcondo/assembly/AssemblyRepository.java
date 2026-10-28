package br.com.tekcondo.assembly;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.assembly.AssemblyEntities.Assembly;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssemblyRepository extends JpaRepository<Assembly, UUID> {

    List<Assembly> findByCondominiumIdOrderByScheduledAtDesc(UUID condominiumId);

    Optional<Assembly> findByIdAndCondominiumId(UUID id, UUID condominiumId);
}
