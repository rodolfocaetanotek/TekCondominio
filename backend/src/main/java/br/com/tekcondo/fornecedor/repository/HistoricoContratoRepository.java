package br.com.tekcondo.fornecedor.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.fornecedor.entity.HistoricoContrato;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoContratoRepository extends JpaRepository<HistoricoContrato, UUID> {

    List<HistoricoContrato> findByIdContratoOrderByCriadoEmDesc(UUID idContrato);
}
