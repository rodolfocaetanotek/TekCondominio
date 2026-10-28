package br.com.tekcondo.estrutura.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.estrutura.entity.Bloco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlocoRepository extends JpaRepository<Bloco, UUID> {

    List<Bloco> findByIdCondominioOrderByNomeAsc(UUID idCondominio);

    Optional<Bloco> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("select b from Bloco b where b.idCondominio = :idCondominio and lower(b.nome) = lower(:nome)")
    Optional<Bloco> buscarPorNome(UUID idCondominio, String nome);

    @Query("select count(u) from Unidade u where u.bloco.id = :idBloco and u.ativo = true")
    long contarUnidadesAtivas(UUID idBloco);
}
