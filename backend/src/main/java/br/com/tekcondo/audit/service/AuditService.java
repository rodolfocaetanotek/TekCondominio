package br.com.tekcondo.audit.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.audit.entity.AuditLog;
import br.com.tekcondo.audit.repository.AuditLogRepository;
import br.com.tekcondo.shared.security.ClientIp;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a trilha de auditoria na MESMA transação da operação auditada: se a operação for revertida,
 * o registro de auditoria também é (não há registro de algo que não aconteceu).
 * Nunca passe senhas, tokens ou segredos em {@code details}.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, CurrentUserProvider currentUserProvider, Clock clock) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /** Auditoria de ação do usuário autenticado na requisição corrente. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String entityType, UUID entityId, Map<String, Object> details) {
        CurrentUser user = currentUserProvider.get();
        save(user.organizationId(), user.userId(), user.email(), action, entityType, entityId, details);
    }

    /** Auditoria com ator explícito (ex.: login, quando ainda não há usuário autenticado no contexto). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(UUID organizationId, UUID userId, String userEmail, AuditAction action, String entityType,
                         UUID entityId, Map<String, Object> details) {
        save(organizationId, userId, userEmail, action, entityType, entityId, details);
    }

    private void save(UUID organizationId, UUID userId, String userEmail, AuditAction action, String entityType,
                      UUID entityId, Map<String, Object> details) {
        repository.save(new AuditLog(organizationId, userId, userEmail, action.name(), entityType,
                entityId == null ? null : entityId.toString(), details == null || details.isEmpty() ? null : details,
                ClientIp.current(), Instant.now(clock)));
    }
}
