package br.com.tekcondo.assembly;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.tekcondo.announcement.Announcement;
import br.com.tekcondo.announcement.AnnouncementRepository;
import br.com.tekcondo.announcement.AnnouncementService;
import br.com.tekcondo.assembly.AssemblyEntities.AgendaItem;
import br.com.tekcondo.assembly.AssemblyEntities.Assembly;
import br.com.tekcondo.assembly.AssemblyEntities.Attendance;
import br.com.tekcondo.assembly.AssemblyEntities.Vote;
import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.UnitRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembleias: pauta, convocação, presença e procurações, votação por unidade (peso 1 ou fração ideal),
 * deliberações com responsável e prazo, e ata. Votos são registrados pela mesa (assembleia presencial ou híbrida),
 * um por unidade presente em cada item, sem alteração posterior.
 */
@Service
public class AssemblyService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZONE);

    public record AgendaRequest(@NotBlank(message = "Informe o item da pauta.") @Size(max = 200) String title,
                                @Size(max = 2000) String description, boolean voting) {
    }

    public record Request(@NotBlank(message = "Informe o título.") @Size(max = 200) String title,
                          @NotNull(message = "Informe o tipo.") Assembly.Kind kind,
                          @NotNull(message = "Informe a data e hora.") Instant scheduledAt,
                          Instant secondCallAt,
                          @NotBlank(message = "Informe o local.") @Size(max = 255) String location,
                          @NotNull Assembly.VoteWeight voteWeight,
                          @NotEmpty(message = "Inclua ao menos um item na pauta.") List<@Valid AgendaRequest> agenda) {
    }

    public record AttendanceRequest(@NotNull(message = "Informe a unidade.") UUID unitId,
                                    @NotBlank(message = "Informe quem está presente.") @Size(max = 150) String attendeeName,
                                    boolean byProxy, @Size(max = 150) String proxyName) {
    }

    public record VoteRequest(@NotNull UUID agendaItemId, @NotNull UUID unitId, @NotNull Vote.Choice choice) {
    }

    public record DeliberationRequest(@Size(max = 2000) String deliberation, @Size(max = 150) String responsible, LocalDate deadline) {
    }

    public record MinutesRequest(@NotBlank(message = "Escreva a ata.") @Size(max = 100000) String minutes) {
    }

    public record Tally(BigDecimal yes, BigDecimal no, BigDecimal abstain, long votes) {
    }

    public record ItemResponse(UUID id, int position, String title, String description, boolean voting, String deliberation,
                               String responsible, LocalDate deadline, Tally tally) {
    }

    public record AttendanceResponse(UUID id, UUID unitId, String unitLabel, String attendeeName, boolean byProxy,
                                     String proxyName, Instant registeredAt) {
    }

    public record Quorum(long unitsPresent, long totalUnits, BigDecimal fractionPresent) {
    }

    public record Summary(UUID id, String title, Assembly.Kind kind, Instant scheduledAt, String location, Assembly.Status status) {
    }

    public record Detail(UUID id, String title, Assembly.Kind kind, Instant scheduledAt, Instant secondCallAt, String location,
                         Assembly.VoteWeight voteWeight, Assembly.Status status, Instant calledAt, Instant closedAt,
                         String cancelReason, String minutes, Long version, List<ItemResponse> agenda,
                         List<AttendanceResponse> attendance, Quorum quorum) {
    }

    private final AssemblyRepository assemblies;
    private final AgendaItemRepository items;
    private final AttendanceRepository attendances;
    private final VoteRepository votes;
    private final UnitRepository units;
    private final AnnouncementRepository announcements;
    private final AnnouncementService announcementService;
    private final CondominiumAccess access;
    private final AuditService auditService;
    private final Clock clock;

    public AssemblyService(AssemblyRepository assemblies, AgendaItemRepository items, AttendanceRepository attendances,
                           VoteRepository votes, UnitRepository units, AnnouncementRepository announcements,
                           AnnouncementService announcementService, CondominiumAccess access, AuditService auditService,
                           Clock clock) {
        this.assemblies = assemblies;
        this.items = items;
        this.attendances = attendances;
        this.votes = votes;
        this.units = units;
        this.announcements = announcements;
        this.announcementService = announcementService;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Summary> list(UUID condominiumId) {
        access.require(condominiumId, Permission.ASSEMBLY_VIEW);
        return assemblies.findByCondominiumIdOrderByScheduledAtDesc(condominiumId).stream()
                .map(a -> new Summary(a.getId(), a.getTitle(), a.getKind(), a.getScheduledAt(), a.getLocation(), a.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detail get(UUID condominiumId, UUID id) {
        access.require(condominiumId, Permission.ASSEMBLY_VIEW);
        return detail(load(condominiumId, id));
    }

    @Transactional
    public Detail create(UUID condominiumId, Request r) {
        Condominium c = access.requireActive(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = new Assembly(c.getOrganizationId(), condominiumId, access.user().userId());
        a.details(r.title().trim(), r.kind(), r.scheduledAt(), r.secondCallAt(), r.location().trim(), r.voteWeight());
        assemblies.save(a);
        saveAgenda(a, r.agenda());
        auditService.record(AuditAction.ASSEMBLY_CREATED, "Assembly", a.getId(), Map.of("title", a.getTitle()));
        return detail(a);
    }

    @Transactional
    public Detail update(UUID condominiumId, UUID id, Request r) {
        access.requireActive(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.details(r.title().trim(), r.kind(), r.scheduledAt(), r.secondCallAt(), r.location().trim(), r.voteWeight());
        items.deleteByAssemblyId(a.getId());
        items.flush();
        saveAgenda(a, r.agenda());
        assemblies.flush();
        auditService.record(AuditAction.ASSEMBLY_UPDATED, "Assembly", id, Map.of("title", a.getTitle()));
        return detail(a);
    }

    /** Convoca: publica comunicado com data, local e pauta para todos os condôminos (notificação + e-mail). */
    @Transactional
    public Detail call(UUID condominiumId, UUID id) {
        Condominium c = access.requireActive(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        if (!a.getScheduledAt().isAfter(Instant.now(clock))) {
            throw ApiException.businessRule("ASSEMBLY_IN_PAST", "A data da assembleia já passou. Ajuste antes de convocar.");
        }
        a.call(Instant.now(clock));
        StringBuilder body = new StringBuilder();
        body.append("Ficam os senhores condôminos convocados para a Assembleia Geral ")
                .append(a.getKind() == Assembly.Kind.ORDINARY ? "Ordinária" : "Extraordinária")
                .append(", a realizar-se em ").append(DATE_TIME.format(a.getScheduledAt()))
                .append(a.getSecondCallAt() == null ? "" : " em primeira convocação, ou " + DATE_TIME.format(a.getSecondCallAt())
                        + " em segunda convocação")
                .append(", no local: ").append(a.getLocation()).append(".\n\nOrdem do dia:\n");
        for (AgendaItem item : items.findByAssemblyIdOrderByPositionAsc(a.getId())) {
            body.append(item.getPosition()).append(". ").append(item.getTitle()).append('\n');
        }
        body.append("\nO condômino poderá ser representado por procurador, mediante procuração.");
        var user = access.user();
        Announcement announcement = announcements.save(new Announcement(c.getOrganizationId(), condominiumId,
                "Convocação: " + a.getTitle(), body.toString(), Announcement.Audience.ALL, null, null, true, true,
                a.getScheduledAt().atZone(ZONE).toLocalDate(), Instant.now(clock), user.userId(), user.name()));
        announcementService.distribute(c, announcement);
        auditService.record(AuditAction.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "CALLED"));
        return detail(a);
    }

    @Transactional
    public Detail start(UUID condominiumId, UUID id) {
        access.requireActive(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.start();
        auditService.record(AuditAction.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "IN_PROGRESS"));
        return detail(a);
    }

    @Transactional
    public Detail close(UUID condominiumId, UUID id) {
        access.require(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.close(Instant.now(clock));
        auditService.record(AuditAction.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "CLOSED"));
        return detail(a);
    }

    @Transactional
    public Detail cancel(UUID condominiumId, UUID id, String reason) {
        access.require(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.cancel(reason.trim());
        auditService.record(AuditAction.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "CANCELLED", "reason", reason.trim()));
        return detail(a);
    }

    @Transactional
    public Detail registerAttendance(UUID condominiumId, UUID id, AttendanceRequest r) {
        access.require(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.requireInProgress();
        Unit unit = units.findByIdAndCondominiumId(r.unitId(), condominiumId).orElseThrow(() -> ApiException.notFound("Unidade"));
        if (attendances.findByAssemblyIdAndUnitId(id, unit.getId()).isPresent()) {
            throw ApiException.conflict("ALREADY_PRESENT", "A unidade " + unit.label() + " já está na lista de presença.");
        }
        attendances.save(new Attendance(id, unit.getId(), r.attendeeName().trim(), r.byProxy(),
                r.proxyName() == null ? null : r.proxyName().trim(), access.user().userId()));
        return detail(a);
    }

    @Transactional
    public Detail removeAttendance(UUID condominiumId, UUID id, UUID attendanceId) {
        access.require(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.requireInProgress();
        Attendance att = attendances.findById(attendanceId).filter(x -> x.getAssemblyId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Presença"));
        if (votes.existsByAssemblyIdAndUnitId(id, att.getUnitId())) {
            throw ApiException.businessRule("ATTENDANCE_HAS_VOTES", "A unidade já votou e não pode ser removida da presença.");
        }
        attendances.delete(att);
        return detail(a);
    }

    @Transactional
    public Detail vote(UUID condominiumId, UUID id, VoteRequest r) {
        access.require(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.requireInProgress();
        AgendaItem item = items.findById(r.agendaItemId()).filter(i -> i.getAssemblyId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Item da pauta"));
        if (!item.isVoting()) {
            throw ApiException.businessRule("ITEM_NOT_VOTING", "Este item da pauta não é deliberativo.");
        }
        Unit unit = units.findByIdAndCondominiumId(r.unitId(), condominiumId).orElseThrow(() -> ApiException.notFound("Unidade"));
        if (attendances.findByAssemblyIdAndUnitId(id, unit.getId()).isEmpty()) {
            throw ApiException.businessRule("UNIT_NOT_PRESENT", "Registre a presença da unidade antes do voto.");
        }
        if (votes.existsByAgendaItemIdAndUnitId(item.getId(), unit.getId())) {
            throw ApiException.conflict("ALREADY_VOTED", "A unidade " + unit.label() + " já votou neste item.");
        }
        BigDecimal weight = a.getVoteWeight() == Assembly.VoteWeight.IDEAL_FRACTION ? unit.getIdealFraction() : BigDecimal.ONE;
        votes.save(new Vote(id, item.getId(), unit.getId(), r.choice(), weight, access.user().userId()));
        auditService.record(AuditAction.VOTE_CAST, "Assembly", id, Map.of("item", item.getPosition(), "unit", unit.label(),
                "choice", r.choice().name()));
        return detail(a);
    }

    @Transactional
    public Detail deliberate(UUID condominiumId, UUID id, UUID itemId, DeliberationRequest r) {
        access.require(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        if (a.getStatus() != Assembly.Status.IN_PROGRESS && a.getStatus() != Assembly.Status.CLOSED) {
            throw ApiException.businessRule("ASSEMBLY_NOT_HELD", "Deliberações são registradas durante ou após a assembleia.");
        }
        AgendaItem item = items.findById(itemId).filter(i -> i.getAssemblyId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Item da pauta"));
        item.deliberate(blank(r.deliberation()), blank(r.responsible()), r.deadline());
        return detail(a);
    }

    @Transactional
    public Detail writeMinutes(UUID condominiumId, UUID id, String minutes) {
        access.require(condominiumId, Permission.ASSEMBLY_EDIT);
        Assembly a = load(condominiumId, id);
        a.writeMinutes(minutes.trim());
        auditService.record(AuditAction.ASSEMBLY_UPDATED, "Assembly", id, Map.of("minutes", "atualizada"));
        return detail(a);
    }

    /** Rascunho da ata gerado a partir da pauta, presença, resultados e deliberações registrados. */
    @Transactional(readOnly = true)
    public String minutesDraft(UUID condominiumId, UUID id) {
        Condominium c = access.require(condominiumId, Permission.ASSEMBLY_VIEW);
        Detail d = detail(load(condominiumId, id));
        StringBuilder sb = new StringBuilder();
        sb.append("ATA DA ASSEMBLEIA GERAL ").append(d.kind() == Assembly.Kind.ORDINARY ? "ORDINÁRIA" : "EXTRAORDINÁRIA")
                .append(" DO ").append(c.getName().toUpperCase()).append("\n\n");
        sb.append("Aos ").append(DATE_TIME.format(d.scheduledAt())).append(", em ").append(d.location())
                .append(", reuniram-se os condôminos, conforme lista de presença, representando ")
                .append(d.quorum().unitsPresent()).append(" de ").append(d.quorum().totalUnits()).append(" unidades (")
                .append(d.quorum().fractionPresent().stripTrailingZeros().toPlainString()).append("% das frações ideais).\n\n");
        sb.append("Critério de votação: ").append(d.voteWeight() == Assembly.VoteWeight.UNIT ? "um voto por unidade" : "fração ideal")
                .append(".\n\nORDEM DO DIA E DELIBERAÇÕES\n");
        for (ItemResponse item : d.agenda()) {
            sb.append('\n').append(item.position()).append(". ").append(item.title()).append('\n');
            if (item.voting() && item.tally().votes() > 0) {
                sb.append("   Votação: favoráveis ").append(fmt(item.tally().yes())).append(", contrários ").append(fmt(item.tally().no()))
                        .append(", abstenções ").append(fmt(item.tally().abstain())).append(".\n");
            }
            if (item.deliberation() != null) {
                sb.append("   Deliberação: ").append(item.deliberation()).append('\n');
            }
            if (item.responsible() != null) {
                sb.append("   Responsável: ").append(item.responsible())
                        .append(item.deadline() == null ? "" : " — prazo: " + item.deadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        .append('\n');
            }
        }
        sb.append("\nNada mais havendo a tratar, encerrou-se a assembleia, lavrando-se a presente ata.\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------

    private void saveAgenda(Assembly a, List<AgendaRequest> agenda) {
        for (int i = 0; i < agenda.size(); i++) {
            AgendaRequest it = agenda.get(i);
            items.save(new AgendaItem(a.getId(), i + 1, it.title().trim(), blank(it.description()), it.voting()));
        }
    }

    private Assembly load(UUID condominiumId, UUID id) {
        return assemblies.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.notFound("Assembleia"));
    }

    private Detail detail(Assembly a) {
        List<Vote> allVotes = votes.findByAssemblyId(a.getId());
        Map<UUID, List<Vote>> byItem = allVotes.stream().collect(Collectors.groupingBy(Vote::getAgendaItemId));
        List<ItemResponse> agenda = items.findByAssemblyIdOrderByPositionAsc(a.getId()).stream().map(i -> {
            Map<Vote.Choice, BigDecimal> sums = new EnumMap<>(Vote.Choice.class);
            List<Vote> list = byItem.getOrDefault(i.getId(), List.of());
            list.forEach(v -> sums.merge(v.getChoice(), v.getWeight(), BigDecimal::add));
            return new ItemResponse(i.getId(), i.getPosition(), i.getTitle(), i.getDescription(), i.isVoting(), i.getDeliberation(),
                    i.getResponsible(), i.getDeadline(), new Tally(sums.getOrDefault(Vote.Choice.YES, BigDecimal.ZERO),
                    sums.getOrDefault(Vote.Choice.NO, BigDecimal.ZERO), sums.getOrDefault(Vote.Choice.ABSTAIN, BigDecimal.ZERO), list.size()));
        }).toList();
        List<Unit> condoUnits = units.findActiveByCondominium(a.getCondominiumId());
        Map<UUID, Unit> unitMap = condoUnits.stream().collect(Collectors.toMap(Unit::getId, Function.identity()));
        List<Attendance> present = attendances.findByAssemblyIdOrderByRegisteredAtAsc(a.getId());
        List<AttendanceResponse> attendance = present.stream().map(x -> new AttendanceResponse(x.getId(), x.getUnitId(),
                unitMap.containsKey(x.getUnitId()) ? unitMap.get(x.getUnitId()).label() : units.findById(x.getUnitId()).map(Unit::label).orElse("?"),
                x.getAttendeeName(), x.isByProxy(), x.getProxyName(), x.getRegisteredAt())).toList();
        BigDecimal fraction = present.stream().map(x -> unitMap.get(x.getUnitId())).filter(u -> u != null)
                .map(Unit::getIdealFraction).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Detail(a.getId(), a.getTitle(), a.getKind(), a.getScheduledAt(), a.getSecondCallAt(), a.getLocation(),
                a.getVoteWeight(), a.getStatus(), a.getCalledAt(), a.getClosedAt(), a.getCancelReason(), a.getMinutes(),
                a.getVersion(), agenda, attendance, new Quorum(present.size(), condoUnits.size(), fraction));
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
