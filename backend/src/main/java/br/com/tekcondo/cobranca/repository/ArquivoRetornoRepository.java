package br.com.tekcondo.cobranca.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.cobranca.entity.ArquivoRetorno;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArquivoRetornoRepository extends JpaRepository<ArquivoRetorno, UUID> {

    boolean existsByIdContaBancariaAndHashArquivo(UUID idContaBancaria, String hashArquivo);

    List<ArquivoRetorno> findTop30ByIdCondominioOrderByCriadoEmDesc(UUID idCondominio);
}
