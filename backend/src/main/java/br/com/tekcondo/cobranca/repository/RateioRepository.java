package br.com.tekcondo.cobranca.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.cobranca.entity.Rateio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RateioRepository extends JpaRepository<Rateio, UUID> {

    List<Rateio> findByIdCondominioOrderByCompetenciaDescCriadoEmDesc(UUID idCondominio);

    Optional<Rateio> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("""
            select count(a) > 0 from Rateio a where a.idCondominio = :idCondominio and a.competencia = :competencia
              and a.idContaPlano = :idConta and a.situacao = br.com.tekcondo.cobranca.entity.Rateio.Situacao.GENERATED
            """)
    boolean existeGerado(UUID idCondominio, LocalDate competencia, UUID idConta);
}
