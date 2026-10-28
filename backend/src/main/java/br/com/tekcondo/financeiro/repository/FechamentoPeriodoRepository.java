package br.com.tekcondo.financeiro.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.FechamentoPeriodo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FechamentoPeriodoRepository extends JpaRepository<FechamentoPeriodo, UUID> {

    List<FechamentoPeriodo> findByIdCondominioOrderByCriadoEmDesc(UUID idCondominio);
}
