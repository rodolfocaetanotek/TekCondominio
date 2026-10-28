package br.com.tekcondo.financeiro.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.Baixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BaixaRepository extends JpaRepository<Baixa, UUID> {

    @Query("select p from Baixa p join fetch p.contaBancaria where p.lancamento.id = :idLancamento order by p.dataPagamento, p.criadoEm")
    List<Baixa> buscarPorLancamento(UUID idLancamento);

    @Query("select p from Baixa p join fetch p.lancamento where p.id = :id and p.idCondominio = :idCondominio")
    Optional<Baixa> buscarNoCondominio(UUID id, UUID idCondominio);

    @Query("select p from Baixa p where p.id in :ids and p.idCondominio = :idCondominio")
    List<Baixa> buscarTodasNoCondominio(Collection<UUID> ids, UUID idCondominio);
}
