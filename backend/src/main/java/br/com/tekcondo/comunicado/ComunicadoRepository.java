package br.com.tekcondo.comunicado;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ComunicadoRepository extends JpaRepository<Comunicado, UUID> {

    @Query("select a from Comunicado a where a.idCondominio = :idCondominio order by a.fixado desc, a.publicadoEm desc")
    List<Comunicado> buscarDoCondominio(UUID idCondominio);

    Optional<Comunicado> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query(value = "select count(*) from announcement_read where announcement_id = :id", nativeQuery = true)
    long contarLeituras(UUID id);

    @Query(value = "select count(*) > 0 from announcement_read where announcement_id = :id and user_id = :idUsuario", nativeQuery = true)
    boolean foiLido(UUID id, UUID idUsuario);

    @Modifying
    @Query(value = """
            insert into announcement_read (announcement_id, user_id, read_at) values (:id, :idUsuario, :agora)
            on conflict do nothing
            """, nativeQuery = true)
    void marcarLido(UUID id, UUID idUsuario, Instant agora);

    /** Usuários do portal vinculados a pessoas com ocupação vigente no público-alvo. */
    @Query(value = """
            select distinct u.id from app_user u
              join unit_occupancy o on o.person_id = u.person_id and o.end_date is null
              join unit un on un.id = o.unit_id
             where u.active and un.condominium_id = :idCondominio
               and (:publicoAlvo = 'ALL' or (:publicoAlvo = 'BUILDING' and un.building_id = cast(:idBloco as uuid))
                    or (:publicoAlvo = 'UNIT' and un.id = cast(:idUnidade as uuid)))
            """, nativeQuery = true)
    List<UUID> usuariosMoradores(UUID idCondominio, String publicoAlvo, UUID idBloco, UUID idUnidade);

    /** E-mails das pessoas (moradores/proprietários) vigentes no público-alvo. */
    @Query(value = """
            select distinct p.email from person p
              join unit_occupancy o on o.person_id = p.id and o.end_date is null
              join unit un on un.id = o.unit_id
             where p.active and p.email is not null and un.condominium_id = :idCondominio
               and (:publicoAlvo = 'ALL' or (:publicoAlvo = 'BUILDING' and un.building_id = cast(:idBloco as uuid))
                    or (:publicoAlvo = 'UNIT' and un.id = cast(:idUnidade as uuid)))
            """, nativeQuery = true)
    List<String> emailsMoradores(UUID idCondominio, String publicoAlvo, UUID idBloco, UUID idUnidade);
}
