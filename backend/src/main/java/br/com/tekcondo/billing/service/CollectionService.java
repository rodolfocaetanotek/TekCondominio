package br.com.tekcondo.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.billing.dto.BillingDtos.AgreementRequest;
import br.com.tekcondo.billing.dto.BillingDtos.AgreementResponse;
import br.com.tekcondo.billing.dto.BillingDtos.DebtLine;
import br.com.tekcondo.billing.dto.BillingDtos.DelinquencyReport;
import br.com.tekcondo.billing.dto.BillingDtos.DelinquencyRow;
import br.com.tekcondo.billing.dto.BillingDtos.NoteRequest;
import br.com.tekcondo.billing.dto.BillingDtos.NoteResponse;
import br.com.tekcondo.billing.dto.BillingDtos.UnitDebt;
import br.com.tekcondo.billing.entity.Agreement;
import br.com.tekcondo.billing.entity.CollectionNote;
import br.com.tekcondo.billing.repository.AgreementRepository;
import br.com.tekcondo.billing.repository.CollectionNoteRepository;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.ContaPlanoRepository;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.financeiro.service.LancamentoService;
import br.com.tekcondo.financeiro.service.EncargosCalculator;
import br.com.tekcondo.financeiro.service.BloqueioPeriodo;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inadimplência, demonstrativo de débitos, histórico de cobrança e acordos de parcelamento.
 * Inadimplência = títulos a receber de unidades, em aberto e vencidos na data de referência.
 */
@Service
public class CollectionService {

    private static final String AGREEMENT_ACCOUNT_CODE = "1.07";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final LancamentoRepository entries;
    private final AgreementRepository agreements;
    private final CollectionNoteRepository notes;
    private final ContaPlanoRepository chartAccounts;
    private final UnidadeRepository units;
    private final VinculoRepository occupancies;
    private final LancamentoService entryService;
    private final BloqueioPeriodo periodLock;
    private final AcessoCondominio access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public CollectionService(LancamentoRepository entries, AgreementRepository agreements, CollectionNoteRepository notes,
                             ContaPlanoRepository chartAccounts, UnidadeRepository units, VinculoRepository occupancies,
                             LancamentoService entryService, BloqueioPeriodo periodLock, AcessoCondominio access, AuditoriaService auditService,
                             Clock clock) {
        this.entries = entries;
        this.agreements = agreements;
        this.notes = notes;
        this.chartAccounts = chartAccounts;
        this.units = units;
        this.occupancies = occupancies;
        this.entryService = entryService;
        this.periodLock = periodLock;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- inadimplência

    @Transactional(readOnly = true)
    public DelinquencyReport delinquency(UUID condominiumId, LocalDate asOfParam) {
        access.exigir(condominiumId, Permissao.BILLING_VIEW);
        LocalDate asOf = asOfParam == null ? LocalDate.now(clock) : asOfParam;
        List<Lancamento> overdue = entries.findAll(overdueReceivables(condominiumId, asOf));
        Map<UUID, List<Lancamento>> byUnit = overdue.stream().collect(Collectors.groupingBy(e -> e.getUnidade().getId(),
                LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Pessoa> responsible = responsibles(byUnit.keySet());
        List<DelinquencyRow> rows = new ArrayList<>();
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalCharges = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<Lancamento>> e : byUnit.entrySet()) {
            BigDecimal principal = BigDecimal.ZERO;
            BigDecimal charges = BigDecimal.ZERO;
            LocalDate oldest = null;
            for (Lancamento entry : e.getValue()) {
                EncargosCalculator.Calculo q = quote(entry, asOf);
                principal = principal.add(q.principal());
                charges = charges.add(q.multa()).add(q.juros());
                oldest = oldest == null || entry.getDataVencimento().isBefore(oldest) ? entry.getDataVencimento() : oldest;
            }
            Unidade unit = e.getValue().getFirst().getUnidade();
            Pessoa p = responsible.get(e.getKey());
            LocalDate lastContact = notes.findByCondominiumIdAndUnitIdOrderByContactDateDescCreatedAtDesc(condominiumId, e.getKey())
                    .stream().findFirst().map(CollectionNote::getContactDate).orElse(null);
            rows.add(new DelinquencyRow(e.getKey(), unit.rotulo(), p == null ? null : p.getNome(), p == null ? null : p.getEmail(),
                    p == null ? null : p.getTelefone(), e.getValue().size(), principal, charges, principal.add(charges), oldest,
                    java.time.temporal.ChronoUnit.DAYS.between(oldest, asOf), lastContact));
            totalPrincipal = totalPrincipal.add(principal);
            totalCharges = totalCharges.add(charges);
        }
        rows.sort(Comparator.comparing(DelinquencyRow::total).reversed());
        BigDecimal issued = entries.findAll(issuedReceivables(condominiumId, asOf.minusMonths(12), asOf)).stream()
                .map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = issued.signum() == 0 ? BigDecimal.ZERO
                : totalPrincipal.multiply(HUNDRED).divide(issued, 2, RoundingMode.HALF_UP);
        return new DelinquencyReport(asOf, totalPrincipal, totalCharges, totalPrincipal.add(totalCharges), issued, rate,
                rows.size(), units.countByIdCondominioAndAtivoTrue(condominiumId), rows);
    }

    /** Demonstrativo de débitos da unidade, com valores atualizados (multa e juros) até a data. */
    @Transactional(readOnly = true)
    public UnitDebt unitDebt(UUID condominiumId, UUID unitId, LocalDate asOfParam) {
        access.exigir(condominiumId, Permissao.BILLING_VIEW);
        Unidade unit = units.findByIdAndIdCondominio(unitId, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        return buildUnitDebt(condominiumId, unit, asOfParam == null ? LocalDate.now(clock) : asOfParam);
    }

    public UnitDebt buildUnitDebt(UUID condominiumId, Unidade unit, LocalDate asOf) {
        List<DebtLine> lines = new ArrayList<>();
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        for (Lancamento e : entries.buscarRecebiveisDaUnidade(condominiumId, unit.getId())) {
            if (e.getSituacao() == Lancamento.Situacao.CANCELLED) {
                continue;
            }
            boolean open = e.getSituacao() == Lancamento.Situacao.OPEN || e.getSituacao() == Lancamento.Situacao.PARTIAL;
            EncargosCalculator.Calculo q = open ? quote(e, asOf)
                    : new EncargosCalculator.Calculo(BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            lines.add(new DebtLine(e.getId(), e.getDescricao(), e.getCompetencia(), e.getDataVencimento(), e.getValor(),
                    e.getValorPago(), open ? e.valorAberto() : BigDecimal.ZERO, e.getSituacao(), q.diasAtraso(), q.multa(),
                    q.juros(), q.total()));
            if (open) {
                principal = principal.add(q.principal());
                charges = charges.add(q.multa()).add(q.juros());
            }
        }
        Pessoa p = responsibles(Set.of(unit.getId())).get(unit.getId());
        List<NoteResponse> history = notes.findByCondominiumIdAndUnitIdOrderByContactDateDescCreatedAtDesc(condominiumId, unit.getId())
                .stream().map(NoteResponse::from).toList();
        List<AgreementResponse> unitAgreements = agreements.findByCondominiumIdOrderByCreatedAtDesc(condominiumId).stream()
                .filter(a -> a.getUnitId().equals(unit.getId())).map(a -> toResponse(a, unit)).toList();
        return new UnitDebt(unit.getId(), unit.rotulo(), p == null ? null : p.getNome(), asOf, principal, charges,
                principal.add(charges), lines, history, unitAgreements);
    }

    @Transactional
    public NoteResponse addNote(UUID condominiumId, UUID unitId, NoteRequest request) {
        Condominio condominium = access.exigir(condominiumId, Permissao.BILLING_EDIT);
        units.findByIdAndIdCondominio(unitId, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        if (request.entryId() != null) {
            Lancamento e = entryService.carregar(condominiumId, request.entryId());
            if (e.getUnidade() == null || !e.getUnidade().getId().equals(unitId)) {
                throw ApiException.regraNegocio("ENTRY_NOT_OF_UNIT", "A cobrança informada não pertence a esta unidade.");
            }
        }
        var user = access.usuario();
        CollectionNote note = notes.save(new CollectionNote(condominium.getIdAdministradora(), condominiumId, unitId,
                request.entryId(), request.contactType(), request.contactDate(), request.note().trim(), user.idUsuario(), user.nome()));
        auditService.registrar(AcaoAuditoria.COLLECTION_NOTE_ADDED, "Unit", unitId, Map.of("type", request.contactType().name()));
        return NoteResponse.from(note);
    }

    // ---------------------------------------------------------------- acordos

    @Transactional(readOnly = true)
    public List<AgreementResponse> agreements(UUID condominiumId) {
        access.exigir(condominiumId, Permissao.BILLING_VIEW);
        return agreements.findByCondominiumIdOrderByCreatedAtDesc(condominiumId).stream()
                .map(a -> toResponse(a, units.findById(a.getUnitId()).orElse(null))).toList();
    }

    @Transactional
    public AgreementResponse createAgreement(UUID condominiumId, AgreementRequest request) {
        Condominio condominium = access.exigirAtivo(condominiumId, Permissao.BILLING_EDIT);
        Unidade unit = units.findByIdAndIdCondominio(request.unitId(), condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        LocalDate today = LocalDate.now(clock);
        if (request.firstDueDate().isBefore(today)) {
            throw ApiException.regraNegocio("FIRST_DUE_IN_PAST", "O vencimento da 1ª parcela não pode estar no passado.");
        }
        List<Lancamento> originals = new ArrayList<>();
        for (UUID id : new HashSet<>(request.entryIds())) {
            Lancamento e = entryService.carregar(condominiumId, id);
            if (e.getNatureza() != Lancamento.Natureza.INCOME || e.getUnidade() == null || !e.getUnidade().getId().equals(unit.getId())) {
                throw ApiException.regraNegocio("ENTRY_NOT_OF_UNIT", "Selecione apenas cobranças desta unidade.");
            }
            if (e.getSituacao() != Lancamento.Situacao.OPEN && e.getSituacao() != Lancamento.Situacao.PARTIAL) {
                throw ApiException.regraNegocio("ENTRY_NOT_OPEN", "A cobrança \"" + e.getDescricao() + "\" não está em aberto.");
            }
            originals.add(e);
        }
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        for (Lancamento e : originals) {
            EncargosCalculator.Calculo q = quote(e, today);
            principal = principal.add(q.principal());
            if (request.includeCharges()) {
                charges = charges.add(q.multa()).add(q.juros());
            }
        }
        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO : request.discount();
        Pessoa responsible = responsibles(Set.of(unit.getId())).get(unit.getId());
        UUID by = access.usuario().idUsuario();
        Agreement agreement = agreements.save(new Agreement(condominium.getIdAdministradora(), condominiumId, unit.getId(),
                responsible == null ? null : responsible.getId(), principal, charges, discount, request.installments(),
                request.firstDueDate(), blank(request.notes()), by));
        originals.forEach(e -> {
            e.renegociar(agreement.getId(), by);
            agreements.linkOriginal(agreement.getId(), e.getId());
        });

        ContaPlano account = chartAccounts.buscarDoSistemaPorCodigo(AGREEMENT_ACCOUNT_CODE).orElseThrow();
        BigDecimal installment = agreement.getTotalAmount().divide(BigDecimal.valueOf(request.installments()), 2, RoundingMode.DOWN);
        BigDecimal last = agreement.getTotalAmount().subtract(installment.multiply(BigDecimal.valueOf(request.installments() - 1L)));
        String ref = agreement.getId().toString().substring(0, 8).toUpperCase();
        for (int i = 0; i < request.installments(); i++) {
            Lancamento e = new Lancamento(condominium.getIdAdministradora(), condominiumId, Lancamento.Natureza.INCOME,
                    Lancamento.Origem.AGREEMENT, by);
            LocalDate due = request.firstDueDate().plusMonths(i);
            e.atualizar("Acordo " + ref + " — parcela " + (i + 1) + "/" + request.installments(), account, null, null, unit,
                    responsible, i == request.installments() - 1 ? last : installment, due, due, null, null, by);
            e.definirEncargos(condominium.getPercentualMulta(), condominium.getPercentualJurosMensal());
            e.vincularAcordo(agreement.getId());
            e.recorrencia(agreement.getId(), i + 1, request.installments());
            entries.save(e);
        }
        entries.flush();
        auditService.registrar(AcaoAuditoria.AGREEMENT_CREATED, "Agreement", agreement.getId(), Map.of("unit", unit.rotulo(),
                "original", principal.toPlainString(), "charges", charges.toPlainString(), "discount", discount.toPlainString(),
                "total", agreement.getTotalAmount().toPlainString(), "installments", request.installments()));
        return toResponse(agreement, unit);
    }

    /** Cancela acordo sem parcelas pagas: as parcelas são canceladas e os débitos originais voltam a ficar em aberto. */
    @Transactional
    public AgreementResponse cancelAgreement(UUID condominiumId, UUID id, String reason) {
        access.exigir(condominiumId, Permissao.BILLING_EDIT);
        Agreement agreement = agreements.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Acordo"));
        List<Lancamento> parcels = entries.findByIdAcordo(id);
        if (parcels.stream().anyMatch(e -> e.getValorPago().signum() > 0)) {
            throw ApiException.regraNegocio("AGREEMENT_HAS_PAYMENTS",
                    "O acordo possui parcelas pagas e não pode ser cancelado. Registre o descumprimento no histórico de cobrança.");
        }
        periodLock.exigirAberto(condominiumId, parcels.stream().map(Lancamento::getCompetencia).toArray(LocalDate[]::new));
        UUID by = access.usuario().idUsuario();
        Instant now = Instant.now(clock);
        agreement.cancel(reason.trim(), by, now);
        parcels.forEach(e -> e.cancelar("Acordo cancelado: " + reason.trim(), by, now));
        agreements.originalEntryIds(id).forEach(entryId -> entries.findById(entryId).ifPresent(e -> e.restaurarDoAcordo(by)));
        entries.flush();
        auditService.registrar(AcaoAuditoria.AGREEMENT_CANCELLED, "Agreement", id, Map.of("reason", reason.trim()));
        return toResponse(agreement, units.findById(agreement.getUnitId()).orElse(null));
    }

    // ---------------------------------------------------------------- apoio

    private EncargosCalculator.Calculo quote(Lancamento e, LocalDate asOf) {
        return EncargosCalculator.calcular(e.valorAberto(), e.getDataVencimento(), asOf, e.getPercentualMulta(), e.getPercentualJurosMensal());
    }

    private static Specification<Lancamento> overdueReceivables(UUID condominiumId, LocalDate asOf) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("unidade").fetch("bloco", JoinType.LEFT);
            }
            return cb.and(cb.equal(root.get("idCondominio"), condominiumId),
                    cb.equal(root.get("natureza"), Lancamento.Natureza.INCOME),
                    cb.isNotNull(root.get("unidade")),
                    root.get("situacao").in(Lancamento.Situacao.OPEN, Lancamento.Situacao.PARTIAL),
                    cb.lessThan(root.get("dataVencimento"), asOf));
        };
    }

    private static Specification<Lancamento> issuedReceivables(UUID condominiumId, LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("idCondominio"), condominiumId),
                cb.equal(root.get("natureza"), Lancamento.Natureza.INCOME),
                cb.isNotNull(root.get("unidade")),
                cb.notEqual(root.get("situacao"), Lancamento.Situacao.CANCELLED),
                cb.notEqual(root.get("origem"), Lancamento.Origem.AGREEMENT),
                cb.between(root.get("dataVencimento"), from, to));
    }

    private Map<UUID, Pessoa> responsibles(Set<UUID> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Pessoa> result = new java.util.HashMap<>();
        occupancies.buscarVigentesPorUnidades(unitIds).stream()
                .collect(Collectors.groupingBy(o -> o.getUnidade().getId()))
                .forEach((unitId, occ) -> occ.stream().filter(Vinculo::isResponsavelFinanceiro).findFirst()
                        .or(() -> occ.stream().filter(o -> o.getTipo() == Vinculo.Tipo.OWNER).findFirst())
                        .ifPresent(o -> result.put(unitId, o.getPessoa())));
        return result;
    }

    private AgreementResponse toResponse(Agreement a, Unidade unit) {
        LocalDate today = LocalDate.now(clock);
        List<Lancamento> parcels = entries.findByIdAcordo(a.getId());
        long paid = parcels.stream().filter(e -> e.getSituacao() == Lancamento.Situacao.PAID).count();
        Agreement.Status status = a.getStatus() == Agreement.Status.ACTIVE && !parcels.isEmpty() && paid == parcels.size()
                ? Agreement.Status.COMPLETED : a.getStatus();
        return new AgreementResponse(a.getId(), a.getUnitId(), unit == null ? null : unit.rotulo(), a.getOriginalAmount(),
                a.getChargesAmount(), a.getDiscount(), a.getTotalAmount(), a.getInstallments(), a.getFirstDueDate(), status,
                paid, a.getNotes(), a.getCancelReason(), a.getCreatedAt(),
                parcels.stream().sorted(Comparator.comparing(Lancamento::getDataVencimento))
                        .map(e -> entryService.resumo(e, today)).toList());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
