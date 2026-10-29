package br.com.tekcondo.assembleia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.assembleia.AssembleiaEntities.Presenca;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresencaRepository extends JpaRepository<Presenca, UUID> {

    List<Presenca> findByIdAssembleiaOrderByRegistradoEmAsc(UUID idAssembleia);

    Optional<Presenca> findByIdAssembleiaAndIdUnidade(UUID idAssembleia, UUID idUnidade);
}
