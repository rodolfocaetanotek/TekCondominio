package br.com.tekcondo.occurrence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.structure.repository.UnitRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ocorrências, reclamações e solicitações de manutenção. */
@Service
public class OccurrenceService {

    public record CreateRequest(@NotBlank(message = "Informe o título.") @Size(max = 150) String title,
                                @NotBlank(message = "Descreva a ocorrência.") @Size(max = 4000) String description,
                                @NotNull(message = "Informe a categoria.") Occurrence.Category category,
                                Occurrence.Priority priority, UUID unitId) {
    }

    public record UpdateRequest(@NotNull Occurrence.Category category, @NotNull Occurrence.Priority priority,
                                @Size(max = 150) String assigneeName, LocalDate dueDate) {
    }

    public record StatusRequest(@NotNull(message = "Informe a nova situação.") Occurrence.Status status,
                                @Size(max = 2000) String message) {
    }

    public record CommentRequest(@NotBlank(message = "Escreva a mensagem.") @Size(max = 2000) String message, boolean internal) {
    }

    public record Summary(UUID id, int number, String title, Occurrence.Category category, Occurrence.Priority priority,
                          Occurrence.Status status, UUID unitId, String unitLabel, String reporterName, String assigneeName,
                          LocalDate dueDate, boolean overdue, Instant createdAt, Instant resolvedAt) {
    }

    public record Interaction(UUID id, String authorName, String message, Occurrence.Status statusChange, boolean internal,
                              Instant createdAt) {
    }

    public record Detail(Summary summary, String description, String resolution, Long version, List<Interaction> interactions) {
    }

    public record Stats(Map<Occurrence.Status, Long> byStatus, long open, long overdue, Double averageResolutionHours) {
    }

    private final OccurrenceRepository repository;
    private final OccurrenceInteractionRepository interactions;
    private final UnitRepository units;
    private final CondominiumAccess access;
    private final NotificacaoService notifications;
    private final AuditoriaService auditService;
    private final Clock clock;

    public OccurrenceService(OccurrenceRepository repository, OccurrenceInteractionRepository interactions, UnitRepository units,
                             CondominiumAccess access, NotificacaoService notifications, AuditoriaService auditService, Clock clock) {
        this.repository = repository;
        this.interactions = interactions;
        this.units = units;
        this.access = access;
        this.notifications = notifications;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<Summary> list(UUID condominiumId, List<Occurrence.Status> statuses, Occurrence.Category category,
                                      Occurrence.Priority priority, String search, Pageable pageable) {
        access.require(condominiumId, Permissao.OCCURRENCE_VIEW);
        return PaginaResponse.of(repository.findAll(spec(condominiumId, statuses, category, priority, search, null), pageable),
                this::summary);
    }

    public Specification<Occurrence> spec(UUID condominiumId, List<Occurrence.Status> statuses, Occurrence.Category category,
                                          Occurrence.Priority priority, String search, java.util.Collection<UUID> unitIds) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("condominiumId"), condominiumId));
            if (statuses != null && !statuses.isEmpty()) p.add(root.get("status").in(statuses));
            if (category != null) p.add(cb.equal(root.get("category"), category));
            if (priority != null) p.add(cb.equal(root.get("priority"), priority));
            if (unitIds != null) p.add(root.get("unitId").in(unitIds));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Detail get(UUID condominiumId, UUID id) {
        access.require(condominiumId, Permissao.OCCURRENCE_VIEW);
        return detail(load(condominiumId, id), true);
    }

    @Transactional
    public Detail create(UUID condominiumId, CreateRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.OCCURRENCE_EDIT);
        var user = access.user();
        return detail(createInternal(condominium, request, user.idUsuario(), user.nome()), true);
    }

    /** Usado também pelo portal do morador (permissão verificada pelo chamador). */
    public Occurrence createInternal(Condominium condominium, CreateRequest request, UUID userId, String userName) {
        if (request.unitId() != null) {
            units.findByIdAndCondominiumId(request.unitId(), condominium.getId()).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        }
        Occurrence o = repository.save(new Occurrence(condominium.getOrganizationId(), condominium.getId(),
                repository.nextNumber(condominium.getId()), request.title().trim(), request.description().trim(),
                request.category(), request.priority() == null ? Occurrence.Priority.MEDIUM : request.priority(),
                request.unitId(), userId, userName));
        auditService.registrar(AcaoAuditoria.OCCURRENCE_CREATED, "Occurrence", o.getId(), Map.of("number", o.getNumber(), "title", o.getTitle()));
        notifications.notificar(condominium.getOrganizationId(),
                notifications.usuariosComPermissao(condominium.getOrganizationId(), condominium.getId(), Permissao.OCCURRENCE_EDIT)
                        .stream().filter(u -> !u.equals(userId)).toList(),
                "Nova ocorrência nº " + o.getNumber(), o.getTitle(), "/ocorrencias/" + o.getId(), null);
        return o;
    }

    @Transactional
    public Detail update(UUID condominiumId, UUID id, UpdateRequest request) {
        access.requireActive(condominiumId, Permissao.OCCURRENCE_EDIT);
        Occurrence o = load(condominiumId, id);
        o.triage(request.category(), request.priority(), blank(request.assigneeName()), request.dueDate());
        repository.flush();
        auditService.registrar(AcaoAuditoria.OCCURRENCE_UPDATED, "Occurrence", id, Map.of("priority", request.priority().name(),
                "assignee", String.valueOf(request.assigneeName()), "dueDate", String.valueOf(request.dueDate())));
        return detail(o, true);
    }

    @Transactional
    public Detail changeStatus(UUID condominiumId, UUID id, StatusRequest request) {
        access.requireActive(condominiumId, Permissao.OCCURRENCE_EDIT);
        Occurrence o = load(condominiumId, id);
        var user = access.user();
        if (request.status() == Occurrence.Status.OPEN && o.getStatus() != Occurrence.Status.OPEN) {
            o.reopen();
        } else {
            o.changeStatus(request.status(), blank(request.message()), Instant.now(clock));
        }
        interactions.save(new OccurrenceInteraction(o.getId(), user.idUsuario(), user.nome(),
                request.message() == null || request.message().isBlank() ? "Situação alterada." : request.message().trim(),
                request.status(), false));
        repository.flush();
        auditService.registrar(AcaoAuditoria.OCCURRENCE_UPDATED, "Occurrence", id, Map.of("status", request.status().name()));
        if (o.getReporterUserId() != null && !o.getReporterUserId().equals(user.idUsuario())) {
            notifications.notificar(o.getOrganizationId(), List.of(o.getReporterUserId()),
                    "Ocorrência nº " + o.getNumber() + " atualizada", statusLabel(request.status()), "/portal/ocorrencias/" + o.getId(), null);
        }
        return detail(o, true);
    }

    @Transactional
    public Detail comment(UUID condominiumId, UUID id, CommentRequest request) {
        access.require(condominiumId, Permissao.OCCURRENCE_EDIT);
        Occurrence o = load(condominiumId, id);
        var user = access.user();
        interactions.save(new OccurrenceInteraction(o.getId(), user.idUsuario(), user.nome(), request.message().trim(), null,
                request.internal()));
        if (!request.internal() && o.getReporterUserId() != null && !o.getReporterUserId().equals(user.idUsuario())) {
            notifications.notificar(o.getOrganizationId(), List.of(o.getReporterUserId()),
                    "Nova mensagem na ocorrência nº " + o.getNumber(), request.message().trim(), "/portal/ocorrencias/" + o.getId(), null);
        }
        return detail(o, true);
    }

    @Transactional(readOnly = true)
    public Stats stats(UUID condominiumId) {
        access.require(condominiumId, Permissao.OCCURRENCE_VIEW);
        LocalDate today = LocalDate.now(clock);
        Map<Occurrence.Status, Long> byStatus = new EnumMap<>(Occurrence.Status.class);
        long overdue = 0;
        long resolvedCount = 0;
        long resolvedHours = 0;
        for (Occurrence o : repository.findAll(spec(condominiumId, null, null, null, null, null))) {
            byStatus.merge(o.getStatus(), 1L, Long::sum);
            if (o.isOverdue(today)) overdue++;
            if (o.getStatus() == Occurrence.Status.RESOLVED && o.getResolvedAt() != null) {
                resolvedCount++;
                resolvedHours += Duration.between(o.getCriadoEm(), o.getResolvedAt()).toHours();
            }
        }
        long open = byStatus.getOrDefault(Occurrence.Status.OPEN, 0L) + byStatus.getOrDefault(Occurrence.Status.IN_PROGRESS, 0L)
                + byStatus.getOrDefault(Occurrence.Status.WAITING, 0L);
        return new Stats(byStatus, open, overdue, resolvedCount == 0 ? null : (double) resolvedHours / resolvedCount);
    }

    // ------------------------------------------------------------------

    public Occurrence load(UUID condominiumId, UUID id) {
        return repository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Ocorrência"));
    }

    public Summary summary(Occurrence o) {
        String unitLabel = o.getUnitId() == null ? null : units.findById(o.getUnitId()).map(u -> u.label()).orElse(null);
        return new Summary(o.getId(), o.getNumber(), o.getTitle(), o.getCategory(), o.getPriority(), o.getStatus(),
                o.getUnitId(), unitLabel, o.getReporterName(), o.getAssigneeName(), o.getDueDate(),
                o.isOverdue(LocalDate.now(clock)), o.getCriadoEm(), o.getResolvedAt());
    }

    public Detail detail(Occurrence o, boolean includeInternal) {
        List<Interaction> list = interactions.findByOccurrenceIdOrderByCreatedAtAsc(o.getId()).stream()
                .filter(i -> includeInternal || !i.isInternal())
                .map(i -> new Interaction(i.getId(), i.getAuthorName(), i.getMessage(), i.getStatusChange(), i.isInternal(),
                        i.getCreatedAt()))
                .toList();
        return new Detail(summary(o), o.getDescription(), o.getResolution(), o.getVersao(), list);
    }

    private static String statusLabel(Occurrence.Status s) {
        return switch (s) {
            case OPEN -> "Reaberta";
            case IN_PROGRESS -> "Em atendimento";
            case WAITING -> "Aguardando";
            case RESOLVED -> "Resolvida";
            case CANCELLED -> "Cancelada";
        };
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
