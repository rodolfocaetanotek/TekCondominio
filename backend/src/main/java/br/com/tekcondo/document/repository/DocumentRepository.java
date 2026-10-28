package br.com.tekcondo.document.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndCondominiumId(UUID id, UUID condominiumId);
}
