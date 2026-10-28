package br.com.tekcondo.organization.repository;

import java.util.UUID;

import br.com.tekcondo.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByCnpj(String cnpj);
}
