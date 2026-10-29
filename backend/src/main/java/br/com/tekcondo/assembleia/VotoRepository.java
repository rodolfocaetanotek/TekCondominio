package br.com.tekcondo.assembleia;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.assembleia.AssembleiaEntities.Voto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VotoRepository extends JpaRepository<Voto, UUID> {

    List<Voto> findByIdAssembleia(UUID idAssembleia);

    boolean existsByIdItemPautaAndIdUnidade(UUID idItemPauta, UUID idUnidade);

    boolean existsByIdAssembleiaAndIdUnidade(UUID idAssembleia, UUID idUnidade);
}
