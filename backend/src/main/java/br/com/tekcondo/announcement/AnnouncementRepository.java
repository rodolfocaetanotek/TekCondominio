package br.com.tekcondo.announcement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("select a from Announcement a where a.condominiumId = :condominiumId order by a.pinned desc, a.publishedAt desc")
    List<Announcement> findByCondominium(UUID condominiumId);

    Optional<Announcement> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query(value = "select count(*) from announcement_read where announcement_id = :id", nativeQuery = true)
    long readCount(UUID id);

    @Query(value = "select count(*) > 0 from announcement_read where announcement_id = :id and user_id = :userId", nativeQuery = true)
    boolean isRead(UUID id, UUID userId);

    @Modifying
    @Query(value = """
            insert into announcement_read (announcement_id, user_id, read_at) values (:id, :userId, :now)
            on conflict do nothing
            """, nativeQuery = true)
    void markRead(UUID id, UUID userId, Instant now);

    /** Usuários do portal vinculados a pessoas com ocupação vigente no público-alvo. */
    @Query(value = """
            select distinct u.id from app_user u
              join unit_occupancy o on o.person_id = u.person_id and o.end_date is null
              join unit un on un.id = o.unit_id
             where u.active and un.condominium_id = :condominiumId
               and (:audience = 'ALL' or (:audience = 'BUILDING' and un.building_id = cast(:buildingId as uuid))
                    or (:audience = 'UNIT' and un.id = cast(:unitId as uuid)))
            """, nativeQuery = true)
    List<UUID> residentUsers(UUID condominiumId, String audience, UUID buildingId, UUID unitId);

    /** E-mails das pessoas (moradores/proprietários) vigentes no público-alvo. */
    @Query(value = """
            select distinct p.email from person p
              join unit_occupancy o on o.person_id = p.id and o.end_date is null
              join unit un on un.id = o.unit_id
             where p.active and p.email is not null and un.condominium_id = :condominiumId
               and (:audience = 'ALL' or (:audience = 'BUILDING' and un.building_id = cast(:buildingId as uuid))
                    or (:audience = 'UNIT' and un.id = cast(:unitId as uuid)))
            """, nativeQuery = true)
    List<String> residentEmails(UUID condominiumId, String audience, UUID buildingId, UUID unitId);
}
