package br.com.tekcondo.audit.controller;

import java.time.Instant;
import java.util.Map;

import br.com.tekcondo.audit.dto.AuditLogResponse;
import br.com.tekcondo.audit.service.AuditQueryService;
import br.com.tekcondo.shared.web.PageRequests;
import br.com.tekcondo.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Auditoria")
public class AuditController {

    private static final Map<String, String> SORTS = Map.of("occurredAt", "occurredAt", "action", "action");

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Trilha de auditoria da organização do usuário")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        return service.list(action, entityType, entityId, from, to,
                PageRequests.of(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }
}
