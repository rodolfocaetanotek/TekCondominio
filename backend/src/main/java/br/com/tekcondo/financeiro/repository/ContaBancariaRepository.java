package br.com.tekcondo.financeiro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.ContaBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContaBancariaRepository extends JpaRepository<ContaBancaria, UUID> {

    List<ContaBancaria> findByIdCondominioOrderByNomeAsc(UUID idCondominio);

    Optional<ContaBancaria> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("select b from ContaBancaria b where b.idCondominio = :idCondominio and lower(b.nome) = lower(:nome)")
    Optional<ContaBancaria> buscarPorNome(UUID idCondominio, String nome);
}
