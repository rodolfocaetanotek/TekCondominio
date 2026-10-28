package br.com.tekcondo.estrutura.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.estrutura.entity.Unidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UnidadeRepository extends JpaRepository<Unidade, UUID>, JpaSpecificationExecutor<Unidade> {

    Optional<Unidade> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    Optional<Unidade> findByIdAndIdAdministradora(UUID id, UUID idAdministradora);

    @Query("select u from Unidade u left join fetch u.bloco where u.idCondominio = :idCondominio and lower(u.identificacao) = lower(:identificacao)")
    List<Unidade> buscarPorIdentificacao(UUID idCondominio, String identificacao);

    @Query("select coalesce(sum(u.fracaoIdeal), 0) from Unidade u where u.idCondominio = :idCondominio and u.ativo = true")
    BigDecimal somarFracoesAtivas(UUID idCondominio);

    long countByIdCondominioAndAtivoTrue(UUID idCondominio);

    @Query("select u from Unidade u left join fetch u.bloco where u.idCondominio = :idCondominio and u.ativo = true order by u.identificacao")
    List<Unidade> buscarAtivasDoCondominio(UUID idCondominio);
}
