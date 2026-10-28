package br.com.tekcondo.financeiro.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransferenciaRepository extends JpaRepository<Transferencia, UUID> {

    @Query("""
            select t from Transferencia t join fetch t.contaOrigem join fetch t.contaDestino
            where t.idCondominio = :idCondominio order by t.data desc, t.criadoEm desc
            """)
    List<Transferencia> buscarDoCondominio(UUID idCondominio);

    Optional<Transferencia> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("select t from Transferencia t where t.id in :ids and t.idCondominio = :idCondominio")
    List<Transferencia> buscarTodasNoCondominio(Collection<UUID> ids, UUID idCondominio);
}
