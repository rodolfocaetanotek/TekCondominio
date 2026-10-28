package br.com.tekcondo.identity.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.identity.access.PermissionGrant;
import br.com.tekcondo.identity.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /**
     * Concessões efetivas de um usuário ATIVO dentro da sua organização. Usuário inativo não recebe
     * nenhuma concessão, mesmo com token ainda válido.
     */
    @Query("""
            select new br.com.tekcondo.identity.access.PermissionGrant(ur.condominiumId, p)
            from UserRole ur
              join ur.role r
              join r.permissions p,
              User u
            where u.id = ur.userId
              and u.active = true
              and ur.userId = :userId
              and ur.organizationId = :organizationId
            """)
    List<PermissionGrant> findGrants(UUID userId, UUID organizationId);

    @Query("select ur from UserRole ur join fetch ur.role where ur.userId = :userId order by ur.createdAt")
    List<UserRole> findByUserIdWithRole(UUID userId);

    @Query("select ur from UserRole ur join fetch ur.role where ur.userId in :userIds")
    List<UserRole> findByUserIdsWithRole(Collection<UUID> userIds);

    @Query("""
            select count(ur) > 0 from UserRole ur
            where ur.userId = :userId and ur.role.id = :roleId and ur.condominiumId is null
            """)
    boolean existsOrganizationWide(UUID userId, UUID roleId);

    @Query("""
            select count(ur) > 0 from UserRole ur
            where ur.userId = :userId and ur.role.id = :roleId and ur.condominiumId = :condominiumId
            """)
    boolean existsInCondominium(UUID userId, UUID roleId, UUID condominiumId);

    /** Administradores ativos (papel de administrador para toda a organização). */
    @Query("""
            select count(distinct ur.userId) from UserRole ur, User u
            where u.id = ur.userId and u.active = true
              and ur.organizationId = :organizationId
              and ur.condominiumId is null
              and ur.role.code = :adminRoleCode
            """)
    long countActiveAdmins(UUID organizationId, String adminRoleCode);

    default long countActiveOrganizationAdmins(UUID organizationId) {
        return countActiveAdmins(organizationId, br.com.tekcondo.identity.entity.Role.ORG_ADMIN);
    }
}
