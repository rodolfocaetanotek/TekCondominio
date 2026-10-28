package br.com.tekcondo.financeiro.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.LinhaOrcamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinhaOrcamentoRepository extends JpaRepository<LinhaOrcamento, UUID> {

    List<LinhaOrcamento> findByIdCondominioAndAno(UUID idCondominio, int ano);
}
