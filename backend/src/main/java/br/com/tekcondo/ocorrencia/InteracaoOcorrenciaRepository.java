package br.com.tekcondo.ocorrencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InteracaoOcorrenciaRepository extends JpaRepository<InteracaoOcorrencia, UUID> {

    List<InteracaoOcorrencia> findByIdOcorrenciaOrderByCriadoEmAsc(UUID idOcorrencia);
}
