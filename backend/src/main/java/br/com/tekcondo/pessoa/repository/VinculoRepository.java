package br.com.tekcondo.pessoa.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.pessoa.entity.Vinculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VinculoRepository extends JpaRepository<Vinculo, UUID> {

    @Query("""
            select v from Vinculo v join fetch v.pessoa join fetch v.unidade u left join fetch u.bloco
            where u.id = :idUnidade order by v.dataFim nulls first, v.dataInicio desc
            """)
    List<Vinculo> buscarPorUnidade(UUID idUnidade);

    @Query("""
            select v from Vinculo v join fetch v.pessoa join fetch v.unidade u left join fetch u.bloco
            where v.pessoa.id = :idPessoa order by v.dataFim nulls first, v.dataInicio desc
            """)
    List<Vinculo> buscarPorPessoa(UUID idPessoa);

    @Query("""
            select v from Vinculo v join fetch v.pessoa p
            where v.unidade.id in :idsUnidade and v.dataFim is null
            """)
    List<Vinculo> buscarVigentesPorUnidades(Collection<UUID> idsUnidade);

    @Query("select v from Vinculo v where v.unidade.id = :idUnidade and v.responsavelFinanceiro = true and v.dataFim is null")
    Optional<Vinculo> buscarResponsavelFinanceiroVigente(UUID idUnidade);

    @Query("""
            select v from Vinculo v join fetch v.unidade u
            where v.pessoa.id = :idPessoa and v.dataFim is null and u.idCondominio = :idCondominio
            """)
    List<Vinculo> buscarVigentesDaPessoaNoCondominio(UUID idPessoa, UUID idCondominio);

    @Query("""
            select count(v) > 0 from Vinculo v
            where v.unidade.id = :idUnidade and v.pessoa.id = :idPessoa and v.tipo = :tipo and v.dataFim is null
            """)
    boolean existeVigente(UUID idUnidade, UUID idPessoa, Vinculo.Tipo tipo);
}
