package br.com.tekcondo.financeiro.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.LinhaExtrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LinhaExtratoRepository extends JpaRepository<LinhaExtrato, UUID> {

    @Query("select l.fitId from LinhaExtrato l where l.idContaBancaria = :idConta and l.fitId in :fitIds")
    Set<String> buscarFitIdsExistentes(UUID idConta, Collection<String> fitIds);

    @Query("""
            select l from LinhaExtrato l where l.idContaBancaria = :idConta
              and (:situacao is null or l.situacao = :situacao) order by l.dataLancamento desc, l.fitId
            """)
    List<LinhaExtrato> buscarPorConta(UUID idConta, LinhaExtrato.Situacao situacao);

    @Query("select l from LinhaExtrato l where l.id = :id and l.idCondominio = :idCondominio")
    Optional<LinhaExtrato> buscarNoCondominio(UUID id, UUID idCondominio);

    Optional<LinhaExtrato> findByIdBaixa(UUID idBaixa);

    Optional<LinhaExtrato> findByIdContaBancariaAndIdTransferencia(UUID idContaBancaria, UUID idTransferencia);
}
