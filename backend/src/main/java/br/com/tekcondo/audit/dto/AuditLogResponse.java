package br.com.tekcondo.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.audit.entity.AuditLog;

public record AuditLogResponse(UUID id, Instant occurredAt, UUID userId, String userEmail, String action,
                               String entityType, String entityId, Map<String, Object> details, String ipAddress) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getUserId(), log.getUserEmail(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails(), log.getIpAddress());
    }
}
