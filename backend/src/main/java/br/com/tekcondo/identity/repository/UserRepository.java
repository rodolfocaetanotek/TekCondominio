package br.com.tekcondo.identity.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(String email);

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<User> findByPersonId(UUID personId);
}
