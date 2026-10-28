package br.com.tekcondo.portal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.announcement.Announcement;
import br.com.tekcondo.announcement.AnnouncementRepository;
import br.com.tekcondo.announcement.AnnouncementService;
import br.com.tekcondo.assembly.AssemblyEntities.Assembly;
import br.com.tekcondo.assembly.AssemblyRepository;
import br.com.tekcondo.billing.dto.BillingDtos.DebtLine;
import br.com.tekcondo.billing.dto.BillingDtos.UnitDebt;
import br.com.tekcondo.billing.service.CollectionService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
import br.com.tekcondo.document.entity.Document;
import br.com.tekcondo.document.repository.DocumentRepository;
import br.com.tekcondo.document.service.DocumentService;
import br.com.tekcondo.identity.access.AccessService;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.occurrence.Occurrence;
import br.com.tekcondo.occurrence.OccurrenceInteraction;
import br.com.tekcondo.occurrence.OccurrenceInteractionRepository;
import br.com.tekcondo.occurrence.OccurrenceRepository;
import br.com.tekcondo.occurrence.OccurrenceService;
import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.repository.OccupancyRepository;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal do morador. O acesso é derivado SOMENTE do vínculo do usuário com o cadastro de pessoa e das ocupações
 * vigentes: o morador vê apenas suas unidades. Cobranças: apenas para proprietário ou responsável financeiro.
 */
@Service
public class PortalService {

    public record PortalUnit(UUID unitId, String label, Occupancy.Role role, boolean financialResponsible, boolean seesCharges) {
    }

    public record PortalCondominium(UUID id, String name, List<PortalUnit> units) {
    }

    public record Me(String name, String email, List<PortalCondominium> condominiums) {
    }

    public record UnitCharges(UUID unitId, String unitLabel, java.math.BigDecimal openTotal, List<DebtLine> lines) {
    }

    public record CalendarEvent(LocalDate date, String type, String title) {
    }

    public record AssemblyInfo(UUID id, String title, Assembly.Kind kind, Instant scheduledAt, String location,
                               Assembly.Status status, String minutes) {
    }

    private record Ctx(CurrentUser user, User account, Condominium condominium, List<Occupancy> occupancies) {

        Set<UUID> unitIds() {
            return occupancies.stream().map(o -> o.getUnit().getId()).collect(Collectors.toSet());
        }
    }

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository users;
    private final AccessService accessService;
    private final CondominiumRepository condominiums;
    private final OccupancyRepository occupancies;
    private final CollectionService collection;
    private final AnnouncementRepository announcements;
    private final AnnouncementService announcementService;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final OccurrenceRepository occurrences;
    private final OccurrenceInteractionRepository interactions;
    private final OccurrenceService occurrenceService;
    private final AssemblyRepository assemblies;
    private final Clock clock;

    public PortalService(CurrentUserProvider currentUserProvider, UserRepository users, AccessService accessService,
                         CondominiumRepository condominiums, OccupancyRepository occupancies, CollectionService collection,
                         AnnouncementRepository announcements, AnnouncementService announcementService,
                         DocumentRepository documents, DocumentService documentService, OccurrenceRepository occurrences,
                         OccurrenceInteractionRepository interactions, OccurrenceService occurrenceService,
                         AssemblyRepository assemblies, Clock clock) {
        this.currentUserProvider = currentUserProvider;
        this.users = users;
        this.accessService = accessService;
        this.condominiums = condominiums;
        this.occupancies = occupancies;
        this.collection = collection;
        this.announcements = announcements;
        this.announcementService = announcementService;
        this.documents = documents;
        this.documentService = documentService;
        this.occurrences = occurrences;
        this.interactions = interactions;
        this.occurrenceService = occurrenceService;
        this.assemblies = assemblies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Me me() {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        User account = linkedAccount(user);
        List<PortalCondominium> list = new ArrayList<>();
        for (UUID condoId : accessService.currentGrants().byCondominium().entrySet().stream()
                .filter(e -> e.getValue().contains(Permission.PORTAL_ACCESS)).map(Map.Entry::getKey).toList()) {
            condominiums.findByIdAndOrganizationId(condoId, user.organizationId()).ifPresent(c -> list.add(new PortalCondominium(
                    c.getId(), c.getName(), occupancies.findCurrentByPersonInCondominium(account.getPersonId(), c.getId()).stream()
                    .map(this::toUnit).sorted(Comparator.comparing(PortalUnit::label)).toList())));
        }
        return new Me(account.getName(), account.getEmail(), list);
    }

    @Transactional(readOnly = true)
    public List<UnitCharges> charges(UUID condominiumId) {
        Ctx ctx = ctx(condominiumId);
        LocalDate today = LocalDate.now(clock);
        return ctx.occupancies().stream().filter(o -> toUnit(o).seesCharges()).map(o -> o.getUnit()).distinct()
                .map(unit -> {
                    UnitDebt debt = collection.buildUnitDebt(condominiumId, unit, today);
                    // Histórico dos últimos 24 meses + tudo que estiver em aberto. Notas internas de cobrança não são expostas.
                    List<DebtLine> lines = debt.lines().stream()
                            .filter(l -> l.remaining().signum() > 0 || !l.dueDate().isBefore(today.minusMonths(24)))
                            .sorted(Comparator.comparing(DebtLine::dueDate).reversed()).toList();
                    return new UnitCharges(unit.getId(), unit.label(), debt.openTotal(), lines);
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementService.Response> announcements(UUID condominiumId) {
        Ctx ctx = ctx(condominiumId);
        LocalDate today = LocalDate.now(clock);
        Set<UUID> buildings = ctx.occupancies().stream().map(o -> o.getUnit().getBuilding()).filter(Objects::nonNull)
                .map(b -> b.getId()).collect(Collectors.toSet());
        return announcements.findByCondominium(condominiumId).stream()
                .filter(a -> a.isVisible(today))
                .filter(a -> a.getAudience() == Announcement.Audience.ALL
                        || a.getAudience() == Announcement.Audience.BUILDING && buildings.contains(a.getBuildingId())
                        || a.getAudience() == Announcement.Audience.UNIT && ctx.unitIds().contains(a.getUnitId()))
                .map(a -> announcementService.toResponse(a, null, announcements.isRead(a.getId(), ctx.user().userId())))
                .toList();
    }

    @Transactional
    public void markAnnouncementRead(UUID condominiumId, UUID id) {
        Ctx ctx = ctx(condominiumId);
        if (announcements(condominiumId).stream().noneMatch(a -> a.id().equals(id))) {
            throw ApiException.notFound("Comunicado");
        }
        announcements.markRead(id, ctx.user().userId(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<DocumentService.DocumentResponse> documents(UUID condominiumId) {
        Ctx ctx = ctx(condominiumId);
        return visibleDocuments(ctx).stream().map(DocumentService.DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentService.FileContent download(UUID condominiumId, UUID id) {
        Ctx ctx = ctx(condominiumId);
        Document d = visibleDocuments(ctx).stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Documento"));
        return documentService.read(d);
    }

    @Transactional(readOnly = true)
    public List<OccurrenceService.Summary> occurrences(UUID condominiumId) {
        Ctx ctx = ctx(condominiumId);
        return mine(ctx).stream().map(occurrenceService::summary).toList();
    }

    @Transactional(readOnly = true)
    public OccurrenceService.Detail occurrence(UUID condominiumId, UUID id) {
        Ctx ctx = ctx(condominiumId);
        return occurrenceService.detail(mineOne(ctx, id), false);
    }

    @Transactional
    public OccurrenceService.Detail openOccurrence(UUID condominiumId, OccurrenceService.CreateRequest request) {
        Ctx ctx = ctx(condominiumId);
        if (request.unitId() != null && !ctx.unitIds().contains(request.unitId())) {
            throw new AccessDeniedException("Unidade não vinculada ao morador");
        }
        // Prioridade é definida pela administração na triagem.
        var safe = new OccurrenceService.CreateRequest(request.title(), request.description(), request.category(),
                Occurrence.Priority.MEDIUM, request.unitId());
        return occurrenceService.detail(occurrenceService.createInternal(ctx.condominium(), safe, ctx.user().userId(),
                ctx.account().getName()), false);
    }

    @Transactional
    public OccurrenceService.Detail commentOccurrence(UUID condominiumId, UUID id, String message) {
        Ctx ctx = ctx(condominiumId);
        Occurrence o = mineOne(ctx, id);
        if (o.getStatus() == Occurrence.Status.RESOLVED || o.getStatus() == Occurrence.Status.CANCELLED) {
            throw ApiException.businessRule("OCCURRENCE_CLOSED", "A ocorrência está encerrada. Abra uma nova, se necessário.");
        }
        interactions.save(new OccurrenceInteraction(o.getId(), ctx.user().userId(), ctx.account().getName(), message.trim(), null, false));
        return occurrenceService.detail(o, false);
    }

    @Transactional(readOnly = true)
    public List<AssemblyInfo> assemblies(UUID condominiumId) {
        ctx(condominiumId);
        return assemblies.findByCondominiumIdOrderByScheduledAtDesc(condominiumId).stream()
                .filter(a -> a.getStatus() != Assembly.Status.DRAFT)
                .map(a -> new AssemblyInfo(a.getId(), a.getTitle(), a.getKind(), a.getScheduledAt(), a.getLocation(), a.getStatus(),
                        a.getStatus() == Assembly.Status.CLOSED ? a.getMinutes() : null))
                .toList();
    }

    /** Calendário: assembleias e vencimentos das cobranças em aberto (de 30 dias atrás a 90 dias à frente). */
    @Transactional(readOnly = true)
    public List<CalendarEvent> calendar(UUID condominiumId) {
        LocalDate today = LocalDate.now(clock);
        List<CalendarEvent> events = new ArrayList<>();
        for (AssemblyInfo a : assemblies(condominiumId)) {
            LocalDate d = a.scheduledAt().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
            if (a.status() != Assembly.Status.CANCELLED && !d.isBefore(today.minusDays(30)) && !d.isAfter(today.plusDays(90))) {
                events.add(new CalendarEvent(d, "ASSEMBLY", a.title() + " — " + a.location()));
            }
        }
        for (UnitCharges u : charges(condominiumId)) {
            for (DebtLine l : u.lines()) {
                if (l.remaining().signum() > 0 && !l.dueDate().isBefore(today.minusDays(30)) && !l.dueDate().isAfter(today.plusDays(90))) {
                    events.add(new CalendarEvent(l.dueDate(), "CHARGE", l.description() + " · " + u.unitLabel()));
                }
            }
        }
        events.sort(Comparator.comparing(CalendarEvent::date));
        return events;
    }

    // ------------------------------------------------------------------

    private Ctx ctx(UUID condominiumId) {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        User account = linkedAccount(user);
        Condominium condominium = condominiums.findByIdAndOrganizationId(condominiumId, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Condomínio"));
        accessService.require(Permission.PORTAL_ACCESS, condominiumId);
        List<Occupancy> current = occupancies.findCurrentByPersonInCondominium(account.getPersonId(), condominiumId);
        if (current.isEmpty()) {
            throw new AccessDeniedException("Sem vínculo vigente com unidades deste condomínio");
        }
        return new Ctx(user, account, condominium, current);
    }

    private User linkedAccount(CurrentUser user) {
        User account = users.findById(user.userId()).orElseThrow(() -> ApiException.notFound("Usuário"));
        if (account.getPersonId() == null) {
            throw new AccessDeniedException("Usuário não vinculado a cadastro de morador");
        }
        return account;
    }

    private PortalUnit toUnit(Occupancy o) {
        return new PortalUnit(o.getUnit().getId(), o.getUnit().label(), o.getRole(), o.isFinancialResponsible(),
                o.getRole() == Occupancy.Role.OWNER || o.isFinancialResponsible());
    }

    private List<Document> visibleDocuments(Ctx ctx) {
        return documents.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("condominiumId"), ctx.condominium().getId()),
                cb.equal(root.get("visibility"), Document.Visibility.RESIDENTS),
                cb.isNull(root.get("archivedAt")),
                cb.or(cb.isNull(root.get("unitId")), root.get("unitId").in(ctx.unitIds()))),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private List<Occurrence> mine(Ctx ctx) {
        return occurrences.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("condominiumId"), ctx.condominium().getId()),
                cb.or(cb.equal(root.get("reporterUserId"), ctx.user().userId()), root.get("unitId").in(ctx.unitIds()))),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Occurrence mineOne(Ctx ctx, UUID id) {
        return mine(ctx).stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow(() -> ApiException.notFound("Ocorrência"));
    }
}
