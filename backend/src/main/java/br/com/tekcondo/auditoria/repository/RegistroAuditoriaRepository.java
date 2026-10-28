package br.com.tekcondo.auditoria.repository;

import java.util.UUID;

import br.com.tekcondo.auditoria.entity.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, UUID>, JpaSpecificationExecutor<RegistroAuditoria> {
}
