package br.com.tekcondo.fornecedor.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.fornecedor.entity.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FornecedorRepository extends JpaRepository<Fornecedor, UUID>, JpaSpecificationExecutor<Fornecedor> {

    Optional<Fornecedor> findByIdAndIdAdministradora(UUID id, UUID idAdministradora);

    Optional<Fornecedor> findByIdAdministradoraAndDocumento(UUID idAdministradora, String documento);
}
