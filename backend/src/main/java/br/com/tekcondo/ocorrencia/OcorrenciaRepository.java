package br.com.tekcondo.ocorrencia;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, UUID>, JpaSpecificationExecutor<Ocorrencia> {

    Optional<Ocorrencia> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("select coalesce(max(o.numero), 0) + 1 from Ocorrencia o where o.idCondominio = :idCondominio")
    int proximoNumero(UUID idCondominio);
}
