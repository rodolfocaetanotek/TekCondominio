package br.com.tekcondo.audit.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.tekcondo.audit.dto.AuditLogResponse;
import br.com.tekcondo.audit.entity.AuditLog;
import br.com.tekcondo.audit.repository.AuditLogRepository;
import br.com.tekcondo.identity.access.AccessService;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import br.com.tekcondo.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final AuditLogRepository repository;
    private final AccessService accessService;
    private final CurrentUserProvider currentUserProvider;

    public AuditQueryService(AuditLogRepository repository, AccessService accessService,
                             CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.accessService = accessService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(String action, String entityType, String entityId, Instant from,
                                               Instant to, Pageable pageable) {
        var organizationId = currentUserProvider.requireOrganizationMember().organizationId();
        accessService.requireOrganizationWide(Permission.AUDIT_VIEW);
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), AuditLogResponse::from);
    }
}
