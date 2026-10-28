package br.com.tekcondo.announcement;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.email.EmailDispatcher;
import br.com.tekcondo.integracao.email.MensagemEmail;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.repository.BlocoRepository;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
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
    private final BlocoRepository buildings;
    private final UnidadeRepository units;
    private final AcessoCondominio access;
    private final NotificacaoService notifications;
    private final EmailDispatcher mail;
    private final AuditoriaService auditService;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository repository, BlocoRepository buildings, UnidadeRepository units,
                               AcessoCondominio access, NotificacaoService notifications, EmailDispatcher mail,
                               AuditoriaService auditService, Clock clock) {
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
        access.exigir(condominiumId, Permissao.ANNOUNCEMENT_VIEW);
        UUID userId = access.usuario().idUsuario();
        return repository.findByCondominium(condominiumId).stream()
                .filter(a -> includeArchived || a.getArchivedAt() == null)
                .map(a -> toResponse(a, repository.readCount(a.getId()), repository.isRead(a.getId(), userId)))
                .toList();
    }

    @Transactional
    public Response publish(UUID condominiumId, Request r) {
        Condominio condominium = access.exigirAtivo(condominiumId, Permissao.ANNOUNCEMENT_EDIT);
        if (r.buildingId() != null) {
            buildings.findByIdAndIdCondominio(r.buildingId(), condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Bloco"));
        }
        if (r.unitId() != null) {
            units.findByIdAndIdCondominio(r.unitId(), condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        }
        var user = access.usuario();
        Announcement a = repository.save(new Announcement(condominium.getIdAdministradora(), condominiumId, r.title().trim(),
                r.body().trim(), r.audience(), r.buildingId(), r.unitId(), r.pinned(), r.sendEmail(), r.expiresOn(),
                Instant.now(clock), user.idUsuario(), user.nome()));
        distribute(condominium, a);
        auditService.registrar(AcaoAuditoria.ANNOUNCEMENT_PUBLISHED, "Announcement", a.getId(), Map.of("title", a.getTitle(),
                "audience", a.getAudience().name(), "email", a.isSendEmail()));
        return toResponse(a, 0L, false);
    }

    /** Usado também na convocação de assembleia. */
    public void distribute(Condominio condominium, Announcement a) {
        List<UUID> recipients = new ArrayList<>(repository.residentUsers(condominium.getId(), a.getAudience().name(),
                a.getBuildingId(), a.getUnitId()));
        recipients.addAll(notifications.usuariosComPermissao(condominium.getIdAdministradora(), condominium.getId(),
                Permissao.ANNOUNCEMENT_VIEW));
        notifications.notificar(condominium.getIdAdministradora(), recipients, "Comunicado: " + a.getTitle(),
                a.getBody().length() > 200 ? a.getBody().substring(0, 200) + "…" : a.getBody(), "/comunicados", null);
        if (a.isSendEmail()) {
            for (String email : repository.residentEmails(condominium.getId(), a.getAudience().name(), a.getBuildingId(), a.getUnitId())) {
                mail.enviarAposCommit(new MensagemEmail(email, "[" + condominium.getNome() + "] " + a.getTitle(),
                        a.getBody() + "\n\n— " + condominium.getNome() + "\nMensagem enviada pelo Tek Condomínio."));
            }
        }
    }

    @Transactional
    public Response archive(UUID condominiumId, UUID id) {
        access.exigir(condominiumId, Permissao.ANNOUNCEMENT_EDIT);
        Announcement a = repository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Comunicado"));
        a.archive(Instant.now(clock));
        auditService.registrar(AcaoAuditoria.ANNOUNCEMENT_ARCHIVED, "Announcement", id, null);
        return toResponse(a, repository.readCount(id), null);
    }

    @Transactional
    public void markRead(UUID condominiumId, UUID id) {
        repository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Comunicado"));
        repository.markRead(id, access.usuario().idUsuario(), Instant.now(clock));
    }

    public Response toResponse(Announcement a, Long readCount, Boolean read) {
        String buildingName = a.getBuildingId() == null ? null : buildings.findById(a.getBuildingId()).map(b -> b.getNome()).orElse(null);
        String unitLabel = a.getUnitId() == null ? null : units.findById(a.getUnitId()).map(u -> u.rotulo()).orElse(null);
        return new Response(a.getId(), a.getTitle(), a.getBody(), a.getAudience(), a.getBuildingId(), buildingName, a.getUnitId(),
                unitLabel, a.isPinned(), a.isSendEmail(), a.getExpiresOn(), a.getPublishedAt(), a.getArchivedAt() != null,
                a.getCreatedByName(), readCount, read);
    }
}
