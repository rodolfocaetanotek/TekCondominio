package br.com.tekcondo.condominium.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.condominium.entity.Condominium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Todas as consultas de negócio recebem organizationId: um registro de outra organização é tratado
 * como inexistente.
 */
public interface CondominiumRepository extends JpaRepository<Condominium, UUID>, JpaSpecificationExecutor<Condominium> {

    Optional<Condominium> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCnpj(UUID organizationId, String cnpj);

    boolean existsByOrganizationIdAndCnpjAndIdNot(UUID organizationId, String cnpj, UUID id);
}
