package br.com.tekcondo.announcement;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.integration.mail.MailDispatcher;
import br.com.tekcondo.integration.mail.MailMessage;
import br.com.tekcondo.notification.NotificationService;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.structure.repository.BuildingRepository;
import br.com.tekcondo.structure.repository.UnitRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comunicados com público-alvo, notificação interna, e-mail opcional e controle de leitura. */
@Service
public class AnnouncementService {

    public record Request(@NotBlank(message = "Informe o título.") @Size(max = 150) String title,
                          @NotBlank(message = "Escreva o comunicado.") @Size(max = 8000) String body,
                          @NotNull(message = "Informe o público.") Announcement.Audience audience,
                          UUID buildingId, UUID unitId, boolean pinned, boolean sendEmail, LocalDate expiresOn) {
    }

    public record Response(UUID id, String title, String body, Announcement.Audience audience, UUID buildingId,
                           String buildingName, UUID unitId, String unitLabel, boolean pinned, boolean sendEmail,
                           LocalDate expiresOn, Instant publishedAt, boolean archived, String createdByName,
                           Long readCount, Boolean read) {
    }

    private final AnnouncementRepository repository;
    private final BuildingRepository buildings;
    private final UnitRepository units;
    private final CondominiumAccess access;
    private final NotificationService notifications;
    private final MailDispatcher mail;
    private final AuditService auditService;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository repository, BuildingRepository buildings, UnitRepository units,
                               CondominiumAccess access, NotificationService notifications, MailDispatcher mail,
                               AuditService auditService, Clock clock) {
        this.repository = repository;
        this.buildings = buildings;
        this.units = units;
        this.access = access;
        this.notifications = notifications;
        this.mail = mail;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID condominiumId, boolean includeArchived) {
        access.require(condominiumId, Permission.ANNOUNCEMENT_VIEW);
        UUID userId = access.user().userId();
        return repository.findByCondominium(condominiumId).stream()
                .filter(a -> includeArchived || a.getArchivedAt() == null)
                .map(a -> toResponse(a, repository.readCount(a.getId()), repository.isRead(a.getId(), userId)))
                .toList();
    }

    @Transactional
    public Response publish(UUID condominiumId, Request r) {
        Condominium condominium = access.requireActive(condominiumId, Permission.ANNOUNCEMENT_EDIT);
        if (r.buildingId() != null) {
            buildings.findByIdAndCondominiumId(r.buildingId(), condominiumId).orElseThrow(() -> ApiException.notFound("Bloco"));
        }
        if (r.unitId() != null) {
            units.findByIdAndCondominiumId(r.unitId(), condominiumId).orElseThrow(() -> ApiException.notFound("Unidade"));
        }
        var user = access.user();
        Announcement a = repository.save(new Announcement(condominium.getOrganizationId(), condominiumId, r.title().trim(),
                r.body().trim(), r.audience(), r.buildingId(), r.unitId(), r.pinned(), r.sendEmail(), r.expiresOn(),
                Instant.now(clock), user.userId(), user.name()));
        distribute(condominium, a);
        auditService.record(AuditAction.ANNOUNCEMENT_PUBLISHED, "Announcement", a.getId(), Map.of("title", a.getTitle(),
                "audience", a.getAudience().name(), "email", a.isSendEmail()));
        return toResponse(a, 0L, false);
    }

    /** Usado também na convocação de assembleia. */
    public void distribute(Condominium condominium, Announcement a) {
        List<UUID> recipients = new ArrayList<>(repository.residentUsers(condominium.getId(), a.getAudience().name(),
                a.getBuildingId(), a.getUnitId()));
        recipients.addAll(notifications.usersWithPermission(condominium.getOrganizationId(), condominium.getId(),
                Permission.ANNOUNCEMENT_VIEW));
        notifications.notify(condominium.getOrganizationId(), recipients, "Comunicado: " + a.getTitle(),
                a.getBody().length() > 200 ? a.getBody().substring(0, 200) + "…" : a.getBody(), "/comunicados", null);
        if (a.isSendEmail()) {
            for (String email : repository.residentEmails(condominium.getId(), a.getAudience().name(), a.getBuildingId(), a.getUnitId())) {
                mail.sendAfterCommit(new MailMessage(email, "[" + condominium.getName() + "] " + a.getTitle(),
                        a.getBody() + "\n\n— " + condominium.getName() + "\nMensagem enviada pelo Tek Condomínio."));
            }
        }
    }

    @Transactional
    public Response archive(UUID condominiumId, UUID id) {
        access.require(condominiumId, Permission.ANNOUNCEMENT_EDIT);
        Announcement a = repository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.notFound("Comunicado"));
        a.archive(Instant.now(clock));
        auditService.record(AuditAction.ANNOUNCEMENT_ARCHIVED, "Announcement", id, null);
        return toResponse(a, repository.readCount(id), null);
    }

    @Transactional
    public void markRead(UUID condominiumId, UUID id) {
        repository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.notFound("Comunicado"));
        repository.markRead(id, access.user().userId(), Instant.now(clock));
    }

    public Response toResponse(Announcement a, Long readCount, Boolean read) {
        String buildingName = a.getBuildingId() == null ? null : buildings.findById(a.getBuildingId()).map(b -> b.getName()).orElse(null);
        String unitLabel = a.getUnitId() == null ? null : units.findById(a.getUnitId()).map(u -> u.label()).orElse(null);
        return new Response(a.getId(), a.getTitle(), a.getBody(), a.getAudience(), a.getBuildingId(), buildingName, a.getUnitId(),
                unitLabel, a.isPinned(), a.isSendEmail(), a.getExpiresOn(), a.getPublishedAt(), a.getArchivedAt() != null,
                a.getCreatedByName(), readCount, read);
    }
}
