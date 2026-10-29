package br.com.tekcondo.assembleia;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.assembleia.AssembleiaEntities.ItemPauta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPautaRepository extends JpaRepository<ItemPauta, UUID> {

    List<ItemPauta> findByIdAssembleiaOrderByPosicaoAsc(UUID idAssembleia);

    void deleteByIdAssembleia(UUID idAssembleia);
}
