package br.com.tekcondo.cobranca.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.cobranca.entity.Acordo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AcordoRepository extends JpaRepository<Acordo, UUID> {

    List<Acordo> findByIdCondominioOrderByCriadoEmDesc(UUID idCondominio);

    Optional<Acordo> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Modifying
    @Query(value = "insert into agreement_original_entry (agreement_id, entry_id) values (:idAcordo, :idLancamento)", nativeQuery = true)
    void vincularOriginal(UUID idAcordo, UUID idLancamento);

    @Query(value = "select entry_id from agreement_original_entry where agreement_id = :idAcordo", nativeQuery = true)
    List<UUID> idsLancamentosOriginais(UUID idAcordo);
}
