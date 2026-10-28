package br.com.tekcondo.cobranca.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.cobranca.entity.NotaCobranca;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaCobrancaRepository extends JpaRepository<NotaCobranca, UUID> {

    List<NotaCobranca> findByIdCondominioAndIdUnidadeOrderByDataContatoDescCriadoEmDesc(UUID idCondominio, UUID idUnidade);
}
