package br.com.tekcondo.people.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.people.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PersonRepository extends JpaRepository<Person, UUID>, JpaSpecificationExecutor<Person> {

    Optional<Person> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Person> findByOrganizationIdAndDocument(UUID organizationId, String document);
}
