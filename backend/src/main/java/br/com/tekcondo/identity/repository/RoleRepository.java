package br.com.tekcondo.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.identity.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("select r from Role r where r.organizationId is null and r.code = :code")
    Optional<Role> findSystemRole(String code);

    /** Papéis disponíveis para a organização: os do sistema e os próprios dela. */
    @Query("""
            select r from Role r
            where r.organizationId is null or r.organizationId = :organizationId
            order by r.scope, r.name
            """)
    List<Role> findAvailableTo(UUID organizationId);
}
