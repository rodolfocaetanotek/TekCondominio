package br.com.tekcondo.documento.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.documento.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentoRepository extends JpaRepository<Documento, UUID>, JpaSpecificationExecutor<Documento> {

    Optional<Documento> findByIdAndIdCondominio(UUID id, UUID idCondominio);
}
