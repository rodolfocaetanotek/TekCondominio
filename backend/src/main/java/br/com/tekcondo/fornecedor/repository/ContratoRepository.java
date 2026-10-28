package br.com.tekcondo.fornecedor.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.fornecedor.entity.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContratoRepository extends JpaRepository<Contrato, UUID> {

    @Query("select c from Contrato c join fetch c.fornecedor where c.idCondominio = :idCondominio order by c.situacao, c.dataFim nulls last")
    List<Contrato> buscarDoCondominio(UUID idCondominio);

    Optional<Contrato> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("select c from Contrato c join fetch c.fornecedor where c.situacao = br.com.tekcondo.fornecedor.entity.Contrato.Situacao.ACTIVE and c.dataFim is not null")
    List<Contrato> buscarAtivosComTermino();
}
