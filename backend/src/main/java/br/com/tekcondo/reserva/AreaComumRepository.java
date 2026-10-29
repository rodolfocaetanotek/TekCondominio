package br.com.tekcondo.reserva;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AreaComumRepository extends JpaRepository<AreaComum, UUID> {

    List<AreaComum> findByIdCondominioOrderByNomeAsc(UUID idCondominio);

    Optional<AreaComum> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("select count(a) > 0 from AreaComum a where a.idCondominio = :idCondominio and lower(a.nome) = lower(:nome) and a.id <> :idIgnorado")
    boolean nomeEmUso(UUID idCondominio, String nome, UUID idIgnorado);

    /** Trava a área até o fim da transação: serializa reservas concorrentes da mesma área. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AreaComum a where a.id = :id and a.idCondominio = :idCondominio")
    Optional<AreaComum> travarNoCondominio(UUID id, UUID idCondominio);
}
