package br.com.tekcondo.financeiro.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.ImportacaoExtrato;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoExtratoRepository extends JpaRepository<ImportacaoExtrato, UUID> {

    boolean existsByIdContaBancariaAndHashArquivo(UUID idContaBancaria, String hashArquivo);

    List<ImportacaoExtrato> findTop20ByIdContaBancariaOrderByCriadoEmDesc(UUID idContaBancaria);
}
