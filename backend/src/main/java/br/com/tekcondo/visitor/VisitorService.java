package br.com.tekcondo.visitor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portaria: autorizações de visitantes, prestadores e entregas. O morador pré-autoriza pelo portal (recebe um código
 * de 6 dígitos); a portaria registra entrada (pelo código ou na chegada) e saída. O morador é avisado da entrada.
 */
@Service
public class VisitorService {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record PassRequest(@NotNull(message = "Informe a unidade.") UUID unitId,
                              @NotNull(message = "Informe o tipo.") VisitorPass.Kind kind,
                              @NotBlank(message = "Informe o nome.") @Size(max = 150) String name,
                              @Size(max = 20) String document,
                              @Size(max = 120) String company,
                              @Pattern(regexp = "^$|^[A-Za-z]{3}-?[0-9][A-Za-z0-9][0-9]{2}$", message = "Placa inválida.") String vehiclePlate,
                              LocalDate validFrom,
                              LocalDate validUntil,
                              @Size(max = 500) String notes) {
    }

    public record CodeRequest(@NotBlank(message = "Informe o código.") @Pattern(regexp = "\\d{6}", message = "O código tem 6 dígitos.") String code) {
    }

    /** {@code document} sai mascarado (apenas os 3 últimos caracteres). */
    public record PassResponse(UUID id, UUID unitId, String unitLabel, VisitorPass.Kind kind, String name, String document,
                               String company, String vehiclePlate, LocalDate validFrom, LocalDate validUntil, String accessCode,
                               VisitorPass.Status status, boolean byResident, String authorizedByName, Instant enteredAt,
                               String enteredByName, Instant leftAt, String notes) {
    }

    private final VisitorPassRepository passes;
    private final UnidadeRepository units;
    private final VinculoRepository occupancies;
    private final UsuarioRepository users;
    private final AcessoCondominio access;
    private final NotificacaoService notifications;
    private final AuditoriaService auditService;
    private final Clock clock;

    public VisitorService(VisitorPassRepository passes, UnidadeRepository units, VinculoRepository occupancies,
                          UsuarioRepository users, AcessoCondominio access, NotificacaoService notifications, AuditoriaService auditService, Clock clock) {
        this.passes = passes;
        this.units = units;
        this.occupancies = occupancies;
        this.users = users;
        this.access = access;
        this.notifications = notifications;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ portaria

    @Transactional(readOnly = true)
    public List<PassResponse> today(UUID condominiumId) {
        access.exigir(condominiumId, Permissao.ACCESS_VIEW);
        LocalDate day = today();
        Instant start = day.atStartOfDay(ZONE).toInstant();
        return passes.findForDay(condominiumId, day, start, day.plusDays(1).atStartOfDay(ZONE).toInstant()).stream()
                .map(v -> toResponse(v, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<PassResponse> history(UUID condominiumId, LocalDate from, LocalDate to) {
        access.exigir(condominiumId, Permissao.ACCESS_VIEW);
        LocalDate end = to == null ? today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end) || start.plusDays(366).isBefore(end)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "Informe um período de até 1 ano.");
        }
        return passes.findCreatedBetween(condominiumId, start.atStartOfDay(ZONE).toInstant(), end.plusDays(1).atStartOfDay(ZONE).toInstant())
                .stream().map(v -> toResponse(v, false)).toList();
    }

    /** Chegada sem pré-autorização: a portaria registra e a entrada já fica marcada. */
    @Transactional
    public PassResponse registerArrival(UUID condominiumId, PassRequest request) {
        Condominio condominium = access.exigirAtivo(condominiumId, Permissao.ACCESS_EDIT);
        var user = access.usuario();
        LocalDate day = today();
        VisitorPass v = create(condominium, request, day, day, false, user.idUsuario(), user.nome());
        v.enter(day, Instant.now(clock), user.nome());
        passes.flush();
        afterEntry(v);
        return toResponse(v, false);
    }

    @Transactional
    public PassResponse checkInByCode(UUID condominiumId, String code) {
        access.exigirAtivo(condominiumId, Permissao.ACCESS_EDIT);
        VisitorPass v = passes.findExpectedByCode(condominiumId, code)
                .orElseThrow(() -> ApiException.naoEncontrado("Autorização com este código"));
        return checkIn(v);
    }

    @Transactional
    public PassResponse checkIn(UUID condominiumId, UUID id) {
        access.exigirAtivo(condominiumId, Permissao.ACCESS_EDIT);
        return checkIn(load(condominiumId, id));
    }

    @Transactional
    public PassResponse checkOut(UUID condominiumId, UUID id) {
        access.exigir(condominiumId, Permissao.ACCESS_EDIT);
        VisitorPass v = load(condominiumId, id);
        v.leave(Instant.now(clock), access.usuario().nome());
        passes.flush();
        auditService.registrar(AcaoAuditoria.VISITOR_LEFT, "VisitorPass", v.getId(), Map.of("unit", unitLabel(v)));
        return toResponse(v, false);
    }

    // ------------------------------------------------------------------ portal (unidades já validadas pelo chamador)

    public PassResponse authorizeAsResident(Condominio condominium, PassRequest request, UUID userId, String userName) {
        LocalDate from = request.validFrom() == null ? today() : request.validFrom();
        LocalDate until = request.validUntil() == null ? from : request.validUntil();
        if (from.isBefore(today())) {
            throw ApiException.regraNegocio("PASS_IN_PAST", "A autorização não pode começar no passado.");
        }
        if (until.isBefore(from) || from.plusDays(90).isBefore(until)) {
            throw ApiException.requisicaoInvalida("INVALID_PERIOD", "Período de validade de até 90 dias.");
        }
        VisitorPass v = create(condominium, request, from, until, true, userId, userName);
        v.code(uniqueCode(condominium.getId()));
        passes.flush();
        return toResponse(v, true);
    }

    public List<PassResponse> ofUnits(UUID condominiumId, Collection<UUID> unitIds) {
        return passes.findByUnits(condominiumId, unitIds).stream().map(v -> toResponse(v, true)).toList();
    }

    public PassResponse cancelAsResident(UUID condominiumId, UUID id, Collection<UUID> unitIds) {
        VisitorPass v = passes.findByIdAndCondominiumId(id, condominiumId).filter(x -> unitIds.contains(x.getUnitId()))
                .orElseThrow(() -> ApiException.naoEncontrado("Autorização"));
        v.cancel();
        auditService.registrar(AcaoAuditoria.VISITOR_PASS_CANCELLED, "VisitorPass", id, Map.of("unit", unitLabel(v)));
        return toResponse(v, true);
    }

    // ------------------------------------------------------------------ LGPD

    /** Anonimiza os registros cujo período terminou há mais de {@code retentionDays} dias. */
    @Transactional
    public int anonymizeOlderThan(int retentionDays) {
        Instant now = Instant.now(clock);
        List<VisitorPass> list = passes.findToAnonymize(today().minusDays(retentionDays));
        list.forEach(v -> v.anonymize(now));
        return list.size();
    }

    // ------------------------------------------------------------------

    private VisitorPass create(Condominio condominium, PassRequest r, LocalDate from, LocalDate until, boolean byResident,
                               UUID userId, String userName) {
        Unidade unit = units.findByIdAndIdCondominio(r.unitId(), condominium.getId()).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        VisitorPass v = passes.save(new VisitorPass(condominium.getIdAdministradora(), condominium.getId(), unit.getId(), r.kind(),
                r.name().trim(), blank(r.document()), blank(r.company()),
                blank(r.vehiclePlate()) == null ? null : r.vehiclePlate().replace("-", "").toUpperCase(Locale.ROOT),
                from, until, blank(r.notes()), byResident, userId, userName));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("unit", unit.rotulo());
        details.put("kind", r.kind().name());
        details.put("validFrom", from.toString());
        details.put("validUntil", until.toString());
        auditService.registrar(AcaoAuditoria.VISITOR_PASS_CREATED, "VisitorPass", v.getId(), details);
        return v;
    }

    private PassResponse checkIn(VisitorPass v) {
        v.enter(today(), Instant.now(clock), access.usuario().nome());
        passes.flush();
        afterEntry(v);
        return toResponse(v, false);
    }

    /** Auditoria e aviso aos moradores da unidade com acesso ao portal. */
    private void afterEntry(VisitorPass v) {
        auditService.registrar(AcaoAuditoria.VISITOR_ENTERED, "VisitorPass", v.getId(), Map.of("unit", unitLabel(v), "kind", v.getKind().name()));
        List<UUID> withPortal = notifications.usuariosComPermissao(v.getOrganizationId(), v.getCondominiumId(), Permissao.PORTAL_ACCESS);
        List<UUID> residents = occupancies.buscarVigentesPorUnidades(List.of(v.getUnitId())).stream()
                .map(o -> users.findByIdPessoa(o.getPessoa().getId())).flatMap(java.util.Optional::stream)
                .map(u -> u.getId()).filter(withPortal::contains).distinct().toList();
        notifications.notificar(v.getOrganizationId(), residents, switch (v.getKind()) {
            case VISITOR -> "Visitante na portaria";
            case SERVICE_PROVIDER -> "Prestador de serviço na portaria";
            case DELIVERY -> "Entrega na portaria";
        }, v.getName() + " — entrada registrada para a unidade " + unitLabel(v), "/portal", null);
    }

    private String uniqueCode(UUID condominiumId) {
        for (int i = 0; i < 20; i++) {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            if (passes.findExpectedByCode(condominiumId, code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar código de acesso único");
    }

    private VisitorPass load(UUID condominiumId, UUID id) {
        return passes.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Autorização"));
    }

    private String unitLabel(VisitorPass v) {
        return units.findById(v.getUnitId()).map(Unidade::rotulo).orElse("?");
    }

    /** O código só é exibido a quem autorizou (portal); a portaria digita o código informado pelo visitante. */
    private PassResponse toResponse(VisitorPass v, boolean showCode) {
        return new PassResponse(v.getId(), v.getUnitId(), unitLabel(v), v.getKind(), v.getName(), mask(v.getDocument()), v.getCompany(),
                v.getVehiclePlate(), v.getValidFrom(), v.getValidUntil(), showCode ? v.getAccessCode() : null, v.getStatus(),
                v.isByResident(), v.getAuthorizedByName(), v.getEnteredAt(), null, v.getLeftAt(), v.getNotes());
    }

    static String mask(String document) {
        if (document == null) return null;
        String d = document.replaceAll("[^0-9A-Za-z]", "");
        return d.length() <= 3 ? "***" : "*".repeat(Math.min(d.length() - 3, 8)) + d.substring(d.length() - 3);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
