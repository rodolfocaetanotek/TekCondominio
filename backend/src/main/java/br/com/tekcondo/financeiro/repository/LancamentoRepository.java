package br.com.tekcondo.financeiro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LancamentoRepository extends JpaRepository<Lancamento, UUID>, JpaSpecificationExecutor<Lancamento> {

    Optional<Lancamento> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("""
            select count(e) > 0 from Lancamento e
            where e.idCondominio = :idCondominio and e.fornecedor.id = :idFornecedor and e.numeroDocumento = :numeroDocumento
              and e.situacao <> br.com.tekcondo.financeiro.entity.Lancamento.Situacao.CANCELLED and e.id <> :idExcluido
            """)
    boolean existeDocumentoFornecedor(UUID idCondominio, UUID idFornecedor, String numeroDocumento, UUID idExcluido);

    List<Lancamento> findByIdRateio(UUID idRateio);

    List<Lancamento> findByIdAcordo(UUID idAcordo);

    @Query("""
            select e from Lancamento e left join fetch e.unidade u left join fetch u.bloco
            where e.idCondominio = :idCondominio and e.natureza = br.com.tekcondo.financeiro.entity.Lancamento.Natureza.INCOME
              and e.unidade.id = :idUnidade order by e.dataVencimento
            """)
    List<Lancamento> buscarRecebiveisDaUnidade(UUID idCondominio, UUID idUnidade);

    Optional<Lancamento> findByIdCondominioAndNumeroCobranca(UUID idCondominio, Long numeroCobranca);
}
