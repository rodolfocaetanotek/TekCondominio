package br.com.tekcondo.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    boolean existsByUserIdAndDedupKey(UUID userId, String dedupKey);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(UUID userId, Instant now);

    /** Usuários ativos com a permissão no condomínio (ou em toda a organização). */
    @Query(value = """
            select distinct ur.user_id from user_role ur
              join role_permission rp on rp.role_id = ur.role_id
              join app_user u on u.id = ur.user_id
             where u.active and ur.organization_id = :organizationId and rp.permission_code = :permission
               and (ur.condominium_id is null or ur.condominium_id = :condominiumId)
            """, nativeQuery = true)
    List<UUID> usersWithPermission(UUID organizationId, UUID condominiumId, String permission);
}
