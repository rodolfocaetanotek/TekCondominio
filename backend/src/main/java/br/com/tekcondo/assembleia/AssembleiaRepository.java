package br.com.tekcondo.assembleia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.assembleia.AssembleiaEntities.Assembleia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssembleiaRepository extends JpaRepository<Assembleia, UUID> {

    List<Assembleia> findByIdCondominioOrderByPrimeiraConvocacaoEmDesc(UUID idCondominio);

    Optional<Assembleia> findByIdAndIdCondominio(UUID id, UUID idCondominio);
}
