package br.com.tekcondo.financeiro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.ContaPlano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContaPlanoRepository extends JpaRepository<ContaPlano, UUID> {

    /** Plano padrão do sistema + contas próprias da administradora. */
    @Query("select a from ContaPlano a where a.idAdministradora is null or a.idAdministradora = :idAdministradora order by a.codigo")
    List<ContaPlano> buscarDisponiveis(UUID idAdministradora);

    @Query("select a from ContaPlano a where a.id = :id and (a.idAdministradora is null or a.idAdministradora = :idAdministradora)")
    Optional<ContaPlano> buscarDisponivel(UUID id, UUID idAdministradora);

    @Query("""
            select count(a) > 0 from ContaPlano a
            where (a.idAdministradora is null or a.idAdministradora = :idAdministradora) and a.codigo = :codigo
            """)
    boolean existeCodigo(UUID idAdministradora, String codigo);

    @Query("select a from ContaPlano a where a.idAdministradora is null and a.codigo = :codigo")
    Optional<ContaPlano> buscarDoSistemaPorCodigo(String codigo);
}
