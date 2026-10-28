package br.com.tekcondo.reservation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.finance.entity.ChartAccount;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.ChartAccountRepository;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.finance.service.PeriodLock;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.notification.NotificationService;
import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.people.repository.OccupancyRepository;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.UnitRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservas de áreas comuns. Regras por área: horário de funcionamento, duração máxima, antecedência mínima/máxima
 * (para moradores), limite mensal por unidade, bloqueio de unidade inadimplente e aprovação prévia. A taxa de uso,
 * quando houver, vira cobrança na unidade ao confirmar a reserva.
 */
@Service
public class ReservationService {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String AREA_FEE_ACCOUNT_CODE = "1.06";
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record AreaRequest(@NotBlank(message = "Informe o nome.") @Size(max = 80) String name,
                              @Size(max = 1000) String description,
                              @Size(max = 2000) String rules,
                              @Min(1) Integer capacity,
                              @DecimalMin(value = "0", message = "A taxa não pode ser negativa.") @Digits(integer = 13, fraction = 2) BigDecimal fee,
                              boolean requiresApproval,
                              boolean blockDelinquent,
                              @NotNull(message = "Informe o horário de abertura.") LocalTime openTime,
                              @NotNull(message = "Informe o horário de fechamento.") LocalTime closeTime,
                              @Min(15) @Max(1440) Integer maxDurationMinutes,
                              @Min(0) @Max(720) Integer minAdvanceHours,
                              @Min(1) @Max(730) Integer maxAdvanceDays,
                              @Min(1) Integer maxPerUnitMonth,
                              Boolean active) {
    }

    public record AreaResponse(UUID id, String name, String description, String rules, Integer capacity, BigDecimal fee,
                               boolean requiresApproval, boolean blockDelinquent, LocalTime openTime, LocalTime closeTime,
                               int maxDurationMinutes, int minAdvanceHours, int maxAdvanceDays, Integer maxPerUnitMonth,
                               boolean active, Long version) {
    }

    public record ReservationRequest(@NotNull(message = "Informe a área.") UUID areaId,
                                     @NotNull(message = "Informe a unidade.") UUID unitId,
                                     @NotNull(message = "Informe a data.") LocalDate date,
                                     @NotNull(message = "Informe o horário inicial.") LocalTime startTime,
                                     @NotNull(message = "Informe o horário final.") LocalTime endTime,
                                     @Min(0) @Max(10000) Integer guests,
                                     @Size(max = 500) String notes) {
    }

    public record ReservationResponse(UUID id, UUID areaId, String areaName, UUID unitId, String unitLabel, LocalDate date,
                                      LocalTime startTime, LocalTime endTime, Integer guests, String notes, Reservation.Status status,
                                      BigDecimal fee, UUID entryId, String requesterName, boolean byResident, String decisionNote,
                                      String cancelReason, Instant createdAt) {
    }

    /** Ocupação de horário sem identificar a unidade (para o portal do morador). */
    public record Slot(UUID areaId, LocalDate date, LocalTime startTime, LocalTime endTime, Reservation.Status status) {
    }

    public record DecisionRequest(@Size(max = 255) String note) {
    }

    public record CancelRequest(@NotBlank(message = "Informe o motivo.") @Size(min = 3, max = 255) String reason) {
    }

    private final CommonAreaRepository areas;
    private final ReservationRepository reservations;
    private final UnitRepository units;
    private final OccupancyRepository occupancies;
    private final FinancialEntryRepository entries;
    private final ChartAccountRepository chartAccounts;
    private final PeriodLock periodLock;
    private final CondominiumAccess access;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final Clock clock;

    public ReservationService(CommonAreaRepository areas, ReservationRepository reservations, UnitRepository units,
                              OccupancyRepository occupancies, FinancialEntryRepository entries, ChartAccountRepository chartAccounts,
                              PeriodLock periodLock, CondominiumAccess access, NotificationService notifications,
                              AuditService auditService, Clock clock) {
        this.areas = areas;
        this.reservations = reservations;
        this.units = units;
        this.occupancies = occupancies;
        this.entries = entries;
        this.chartAccounts = chartAccounts;
        this.periodLock = periodLock;
        this.access = access;
        this.notifications = notifications;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ áreas

    @Transactional(readOnly = true)
    public List<AreaResponse> areas(UUID condominiumId) {
        access.require(condominiumId, Permission.RESERVATION_VIEW);
        return areas.findByCondominiumIdOrderByNameAsc(condominiumId).stream().map(ReservationService::toArea).toList();
    }

    @Transactional
    public AreaResponse saveArea(UUID condominiumId, UUID id, AreaRequest r) {
        Condominium condominium = access.requireActive(condominiumId, Permission.RESERVATION_EDIT);
        if (!r.closeTime().isAfter(r.openTime())) {
            throw ApiException.badRequest("INVALID_HOURS", "O horário de fechamento deve ser posterior ao de abertura.");
        }
        CommonArea area = id == null ? new CommonArea(condominium.getOrganizationId(), condominiumId)
                : areas.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.notFound("Área comum"));
        if (areas.nameTaken(condominiumId, r.name().trim(), area.getId())) {
            throw ApiException.conflict("AREA_NAME_TAKEN", "Já existe uma área com este nome.");
        }
        area.update(r.name().trim(), blank(r.description()), blank(r.rules()), r.capacity(), r.fee(), r.requiresApproval(),
                r.blockDelinquent(), r.openTime(), r.closeTime(), r.maxDurationMinutes() == null ? 720 : r.maxDurationMinutes(),
                r.minAdvanceHours() == null ? 24 : r.minAdvanceHours(), r.maxAdvanceDays() == null ? 90 : r.maxAdvanceDays(),
                r.maxPerUnitMonth());
        if (r.active() != null) {
            area.setActive(r.active());
        }
        areas.saveAndFlush(area);
        auditService.record(AuditAction.COMMON_AREA_SAVED, "CommonArea", area.getId(), Map.of("name", area.getName(),
                "fee", area.getFee().toPlainString(), "active", area.isActive()));
        return toArea(area);
    }

    // ------------------------------------------------------------------ reservas (administração)

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(UUID condominiumId, UUID areaId, LocalDate from, LocalDate to) {
        access.require(condominiumId, Permission.RESERVATION_VIEW);
        LocalDate start = from == null ? today().minusDays(7) : from;
        LocalDate end = to == null ? start.plusDays(60) : to;
        return reservations.findInPeriod(condominiumId, areaId, start, end).stream().map(this::toResponse).toList();
    }

    /** Reserva lançada pela administração (ex.: pedido por telefone): dispensa aprovação e antecedência mínima. */
    @Transactional
    public ReservationResponse create(UUID condominiumId, ReservationRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permission.RESERVATION_EDIT);
        var user = access.user();
        return toResponse(book(condominium, request, user.userId(), user.name(), false));
    }

    @Transactional
    public ReservationResponse decide(UUID condominiumId, UUID id, boolean approved, String note) {
        Condominium condominium = access.requireActive(condominiumId, Permission.RESERVATION_EDIT);
        Reservation r = load(condominiumId, id);
        if (!approved && (note == null || note.isBlank())) {
            throw ApiException.badRequest("REASON_REQUIRED", "Informe o motivo da recusa.");
        }
        r.decide(approved, blank(note), access.user().userId(), Instant.now(clock));
        if (approved) {
            charge(condominium, r);
        }
        reservations.flush();
        auditService.record(AuditAction.RESERVATION_DECIDED, "Reservation", id, Map.of("approved", approved, "note", String.valueOf(note)));
        if (r.getRequestedBy() != null) {
            notifications.notify(r.getOrganizationId(), List.of(r.getRequestedBy()),
                    "Reserva " + (approved ? "confirmada" : "recusada"), describe(r) + (note == null || note.isBlank() ? "" : " — " + note.trim()),
                    "/portal", null);
        }
        return toResponse(r);
    }

    @Transactional
    public ReservationResponse cancel(UUID condominiumId, UUID id, String reason) {
        access.require(condominiumId, Permission.RESERVATION_EDIT);
        Reservation r = load(condominiumId, id);
        cancelInternal(r, reason);
        if (r.getRequestedBy() != null && r.isByResident()) {
            notifications.notify(r.getOrganizationId(), List.of(r.getRequestedBy()), "Reserva cancelada pela administração",
                    describe(r) + " — " + reason.trim(), "/portal", null);
        }
        return toResponse(r);
    }

    // ------------------------------------------------------------------ portal (unidade já validada pelo chamador)

    @Transactional(readOnly = true)
    public List<AreaResponse> activeAreas(UUID condominiumId) {
        return areas.findByCondominiumIdOrderByNameAsc(condominiumId).stream().filter(CommonArea::isActive)
                .map(ReservationService::toArea).toList();
    }

    @Transactional(readOnly = true)
    public List<Slot> occupancy(UUID condominiumId, UUID areaId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(120).isBefore(to)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Consulte um período de até 120 dias.");
        }
        return reservations.findInPeriod(condominiumId, areaId, from, to).stream().filter(r -> r.getStatus().isActive())
                .map(r -> new Slot(r.getAreaId(), r.getDate(), r.getStartTime(), r.getEndTime(), r.getStatus())).toList();
    }

    public ReservationResponse bookAsResident(Condominium condominium, ReservationRequest request, UUID userId, String userName) {
        Reservation r = book(condominium, request, userId, userName, true);
        if (r.getStatus() == Reservation.Status.PENDING) {
            notifications.notify(condominium.getOrganizationId(),
                    notifications.usersWithPermission(condominium.getOrganizationId(), condominium.getId(), Permission.RESERVATION_EDIT),
                    "Reserva aguardando aprovação", describe(r) + " · " + userName, "/reservas", null);
        }
        return toResponse(r);
    }

    public List<ReservationResponse> ofUnits(UUID condominiumId, Collection<UUID> unitIds) {
        return reservations.findByUnits(condominiumId, unitIds).stream().map(this::toResponse).toList();
    }

    public ReservationResponse cancelAsResident(UUID condominiumId, UUID id, Collection<UUID> unitIds, String reason) {
        Reservation r = reservations.findByIdAndCondominiumId(id, condominiumId).filter(x -> unitIds.contains(x.getUnitId()))
                .orElseThrow(() -> ApiException.notFound("Reserva"));
        if (!r.getDate().atTime(r.getStartTime()).isAfter(now())) {
            throw ApiException.businessRule("RESERVATION_STARTED", "A reserva já começou ou passou e não pode ser cancelada pelo portal.");
        }
        cancelInternal(r, reason);
        return toResponse(r);
    }

    // ------------------------------------------------------------------ regras

    private Reservation book(Condominium condominium, ReservationRequest q, UUID userId, String userName, boolean byResident) {
        // Trava a área: duas reservas simultâneas do mesmo horário são serializadas (o banco também impede sobreposição).
        CommonArea area = areas.lockInCondominium(q.areaId(), condominium.getId()).orElseThrow(() -> ApiException.notFound("Área comum"));
        Unit unit = units.findByIdAndCondominiumId(q.unitId(), condominium.getId()).orElseThrow(() -> ApiException.notFound("Unidade"));
        if (!area.isActive()) {
            throw ApiException.businessRule("AREA_INACTIVE", "A área " + area.getName() + " não está disponível para reservas.");
        }
        if (!unit.isActive()) {
            throw ApiException.businessRule("UNIT_INACTIVE", "A unidade está inativa.");
        }
        validateWindow(area, q, byResident);
        if (reservations.overlaps(area.getId(), q.date(), q.startTime(), q.endTime())) {
            throw ApiException.conflict("RESERVATION_CONFLICT", "Já existe reserva para " + area.getName() + " neste horário.");
        }
        if (area.getMaxPerUnitMonth() != null) {
            LocalDate first = q.date().withDayOfMonth(1);
            long count = reservations.countActiveForUnit(area.getId(), unit.getId(), first, first.plusMonths(1).minusDays(1));
            if (count >= area.getMaxPerUnitMonth()) {
                throw ApiException.businessRule("RESERVATION_LIMIT", "A unidade já atingiu o limite de " + area.getMaxPerUnitMonth()
                        + " reserva(s) por mês nesta área.");
            }
        }
        if (area.isBlockDelinquent() && hasOverdueCharges(condominium.getId(), unit.getId())) {
            throw ApiException.businessRule("UNIT_DELINQUENT",
                    "A unidade possui cobranças vencidas. Conforme o regulamento desta área, a reserva fica bloqueada até a regularização.");
        }
        Reservation r = reservations.save(new Reservation(area, unit.getId(), q.date(), q.startTime(), q.endTime(), q.guests(),
                blank(q.notes()), userId, userName, byResident));
        if (r.getStatus() == Reservation.Status.CONFIRMED) {
            charge(condominium, r);
        }
        reservations.flush();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("area", area.getName());
        details.put("unit", unit.label());
        details.put("date", q.date().toString());
        details.put("time", q.startTime() + "-" + q.endTime());
        details.put("status", r.getStatus().name());
        auditService.record(AuditAction.RESERVATION_CREATED, "Reservation", r.getId(), details);
        return r;
    }

    private void validateWindow(CommonArea area, ReservationRequest q, boolean byResident) {
        if (!q.endTime().isAfter(q.startTime())) {
            throw ApiException.badRequest("INVALID_TIME", "O horário final deve ser posterior ao inicial.");
        }
        if (q.startTime().isBefore(area.getOpenTime()) || q.endTime().isAfter(area.getCloseTime())) {
            throw ApiException.businessRule("OUTSIDE_HOURS", "A área funciona das " + area.getOpenTime() + " às " + area.getCloseTime() + ".");
        }
        if (Duration.between(q.startTime(), q.endTime()).toMinutes() > area.getMaxDurationMinutes()) {
            throw ApiException.businessRule("DURATION_EXCEEDED", "Duração máxima de " + area.getMaxDurationMinutes() + " minutos.");
        }
        LocalDateTime start = q.date().atTime(q.startTime());
        LocalDateTime now = now();
        if (!start.isAfter(now)) {
            throw ApiException.businessRule("RESERVATION_IN_PAST", "Não é possível reservar horários passados.");
        }
        if (byResident && start.isBefore(now.plusHours(area.getMinAdvanceHours()))) {
            throw ApiException.businessRule("ADVANCE_REQUIRED", "Reservas desta área exigem antecedência mínima de "
                    + area.getMinAdvanceHours() + " hora(s).");
        }
        if (q.date().isAfter(today().plusDays(area.getMaxAdvanceDays()))) {
            throw ApiException.businessRule("TOO_FAR_AHEAD", "Reservas abertas com até " + area.getMaxAdvanceDays() + " dias de antecedência.");
        }
    }

    private boolean hasOverdueCharges(UUID condominiumId, UUID unitId) {
        LocalDate today = today();
        return entries.findReceivablesOfUnit(condominiumId, unitId).stream().anyMatch(e -> e.isOverdue(today));
    }

    /** Gera a cobrança da taxa de uso na unidade (título a receber, com multa/juros do condomínio). */
    private void charge(Condominium condominium, Reservation r) {
        if (r.getFee().signum() <= 0 || r.getEntryId() != null) {
            return;
        }
        LocalDate due = r.getDate().isBefore(today()) ? today() : r.getDate();
        periodLock.ensureOpen(condominium.getId(), due.withDayOfMonth(1));
        ChartAccount account = chartAccounts.findSystemByCode(AREA_FEE_ACCOUNT_CODE).orElseThrow();
        Unit unit = units.findById(r.getUnitId()).orElseThrow();
        Person responsible = occupancies.findCurrentFinancialResponsible(unit.getId()).map(Occupancy::getPerson)
                .or(() -> occupancies.findCurrentByUnits(List.of(unit.getId())).stream()
                        .filter(o -> o.getRole() == Occupancy.Role.OWNER).map(Occupancy::getPerson).findFirst())
                .orElse(null);
        UUID by = access.user().userId();
        FinancialEntry entry = new FinancialEntry(condominium.getOrganizationId(), condominium.getId(), FinancialEntry.Kind.INCOME,
                FinancialEntry.Origin.MANUAL, by);
        entry.details("Taxa de uso — " + areaName(r) + " em " + r.getDate().format(BR), account, null, null, unit, responsible,
                r.getFee(), due, due, null, "Reserva " + r.getId(), by);
        entry.lateFees(condominium.getFinePercent(), condominium.getInterestMonthlyPercent());
        entries.save(entry);
        r.linkCharge(entry.getId());
    }

    private void cancelInternal(Reservation r, String reason) {
        Instant now = Instant.now(clock);
        r.cancel(reason.trim(), now);
        if (r.getEntryId() != null) {
            entries.findById(r.getEntryId()).filter(e -> e.getStatus() == FinancialEntry.Status.OPEN).ifPresent(e -> {
                periodLock.ensureOpen(r.getCondominiumId(), e.getCompetence());
                e.cancel("Reserva cancelada: " + reason.trim(), access.user().userId(), now);
            });
        }
        reservations.flush();
        auditService.record(AuditAction.RESERVATION_CANCELLED, "Reservation", r.getId(), Map.of("reason", reason.trim()));
    }

    private Reservation load(UUID condominiumId, UUID id) {
        return reservations.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.notFound("Reserva"));
    }

    private String areaName(Reservation r) {
        return areas.findById(r.getAreaId()).map(CommonArea::getName).orElse("área comum");
    }

    private String describe(Reservation r) {
        return areaName(r) + " em " + r.getDate().format(BR) + ", " + r.getStartTime() + "–" + r.getEndTime();
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(r.getId(), r.getAreaId(), areaName(r), r.getUnitId(),
                units.findById(r.getUnitId()).map(Unit::label).orElse(null), r.getDate(), r.getStartTime(), r.getEndTime(),
                r.getGuests(), r.getNotes(), r.getStatus(), r.getFee(), r.getEntryId(), r.getRequesterName(), r.isByResident(),
                r.getDecisionNote(), r.getCancelReason(), r.getCreatedAt());
    }

    private static AreaResponse toArea(CommonArea a) {
        return new AreaResponse(a.getId(), a.getName(), a.getDescription(), a.getRules(), a.getCapacity(), a.getFee(),
                a.isRequiresApproval(), a.isBlockDelinquent(), a.getOpenTime(), a.getCloseTime(), a.getMaxDurationMinutes(),
                a.getMinAdvanceHours(), a.getMaxAdvanceDays(), a.getMaxPerUnitMonth(), a.isActive(), a.getVersion());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZONE));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
