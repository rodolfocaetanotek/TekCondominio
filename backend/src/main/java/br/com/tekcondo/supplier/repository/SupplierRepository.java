package br.com.tekcondo.supplier.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.supplier.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {

    Optional<Supplier> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Supplier> findByOrganizationIdAndDocument(UUID organizationId, String document);
}
