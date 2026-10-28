package br.com.tekcondo.financeiro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.CentroCusto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CentroCustoRepository extends JpaRepository<CentroCusto, UUID> {

    List<CentroCusto> findByIdCondominioOrderByNomeAsc(UUID idCondominio);

    Optional<CentroCusto> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("select c from CentroCusto c where c.idCondominio = :idCondominio and lower(c.nome) = lower(:nome)")
    Optional<CentroCusto> buscarPorNome(UUID idCondominio, String nome);
}
