package br.com.tekcondo.condominio.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.condominio.entity.Condominio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Todas as consultas de negócio recebem idAdministradora: um registro de outra administradora é tratado
 * como inexistente.
 */
public interface CondominioRepository extends JpaRepository<Condominio, UUID>, JpaSpecificationExecutor<Condominio> {

    Optional<Condominio> findByIdAndIdAdministradora(UUID id, UUID idAdministradora);

    boolean existsByIdAdministradoraAndCnpj(UUID idAdministradora, String cnpj);

    boolean existsByIdAdministradoraAndCnpjAndIdNot(UUID idAdministradora, String cnpj, UUID id);
}
