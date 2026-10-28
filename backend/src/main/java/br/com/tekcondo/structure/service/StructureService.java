package br.com.tekcondo.structure.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.people.repository.OccupancyRepository;
import br.com.tekcondo.people.repository.PersonRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.io.NumerosBrasileiros;
import br.com.tekcondo.comum.io.CsvReader;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.structure.dto.StructureDtos.BuildingRequest;
import br.com.tekcondo.structure.dto.StructureDtos.BuildingResponse;
import br.com.tekcondo.structure.dto.StructureDtos.FractionSummary;
import br.com.tekcondo.structure.dto.StructureDtos.ImportError;
import br.com.tekcondo.structure.dto.StructureDtos.ImportResult;
import br.com.tekcondo.structure.dto.StructureDtos.UnitRequest;
import br.com.tekcondo.structure.dto.StructureDtos.UnitResponse;
import br.com.tekcondo.structure.entity.Building;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.BuildingRepository;
import br.com.tekcondo.structure.repository.UnitRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Blocos, unidades, frações ideais e importação de planilha. */
@Service
public class StructureService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_IMPORT_LINES = 5000;
    private static final Map<String, Unit.Type> TYPE_ALIASES = Map.ofEntries(
            Map.entry("apartamento", Unit.Type.APARTMENT), Map.entry("apto", Unit.Type.APARTMENT),
            Map.entry("ap", Unit.Type.APARTMENT), Map.entry("casa", Unit.Type.HOUSE),
            Map.entry("sala", Unit.Type.ROOM), Map.entry("loja", Unit.Type.STORE),
            Map.entry("vaga", Unit.Type.PARKING), Map.entry("garagem", Unit.Type.PARKING),
            Map.entry("box", Unit.Type.PARKING), Map.entry("outro", Unit.Type.OTHER), Map.entry("outros", Unit.Type.OTHER));

    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final PersonRepository personRepository;
    private final OccupancyRepository occupancyRepository;
    private final CondominiumAccess access;
    private final AuditoriaService auditService;
    private final Clock clock;

    public StructureService(BuildingRepository buildingRepository, UnitRepository unitRepository,
                            PersonRepository personRepository, OccupancyRepository occupancyRepository,
                            CondominiumAccess access, AuditoriaService auditService, Clock clock) {
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.personRepository = personRepository;
        this.occupancyRepository = occupancyRepository;
        this.access = access;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- blocos

    @Transactional(readOnly = true)
    public List<BuildingResponse> buildings(UUID condominiumId) {
        access.require(condominiumId, Permissao.STRUCTURE_VIEW);
        return buildingRepository.findByCondominiumIdOrderByNameAsc(condominiumId).stream()
                .map(b -> new BuildingResponse(b.getId(), b.getName(), b.getDescription(), b.isActive(),
                        buildingRepository.countActiveUnits(b.getId())))
                .toList();
    }

    @Transactional
    public BuildingResponse createBuilding(UUID condominiumId, BuildingRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.STRUCTURE_EDIT);
        ensureBuildingNameFree(condominiumId, request.name(), null);
        Building building = buildingRepository.save(new Building(condominium.getOrganizationId(), condominiumId,
                request.name(), request.description()));
        auditService.registrar(AcaoAuditoria.BUILDING_CREATED, "Building", building.getId(), Map.of("name", building.getName()));
        return new BuildingResponse(building.getId(), building.getName(), building.getDescription(), true, 0);
    }

    @Transactional
    public BuildingResponse updateBuilding(UUID condominiumId, UUID id, BuildingRequest request) {
        access.requireActive(condominiumId, Permissao.STRUCTURE_EDIT);
        Building building = loadBuilding(condominiumId, id);
        ensureBuildingNameFree(condominiumId, request.name(), id);
        String before = building.getName();
        building.update(request.name(), request.description());
        auditService.registrar(AcaoAuditoria.BUILDING_UPDATED, "Building", id, Map.of("name", Map.of("from", before, "to", building.getName())));
        return new BuildingResponse(id, building.getName(), building.getDescription(), building.isActive(),
                buildingRepository.countActiveUnits(id));
    }

    @Transactional
    public BuildingResponse setBuildingActive(UUID condominiumId, UUID id, boolean active) {
        access.requireActive(condominiumId, Permissao.STRUCTURE_EDIT);
        Building building = loadBuilding(condominiumId, id);
        long units = buildingRepository.countActiveUnits(id);
        if (!active && units > 0) {
            throw ApiException.regraNegocio("BUILDING_HAS_UNITS",
                    "O bloco possui " + units + " unidade(s) ativa(s). Inative ou mova as unidades antes.");
        }
        building.setActive(active);
        auditService.registrar(active ? AcaoAuditoria.BUILDING_ACTIVATED : AcaoAuditoria.BUILDING_DEACTIVATED, "Building", id, null);
        return new BuildingResponse(id, building.getName(), building.getDescription(), active, units);
    }

    // ---------------------------------------------------------------- unidades

    @Transactional(readOnly = true)
    public PaginaResponse<UnitResponse> units(UUID condominiumId, String search, UUID buildingId, Boolean active,
                                            Pageable pageable) {
        access.require(condominiumId, Permissao.STRUCTURE_VIEW);
        Specification<Unit> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("condominiumId"), condominiumId));
            if (search != null && !search.isBlank()) {
                p.add(cb.like(cb.lower(root.get("identifier")), "%" + search.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (buildingId != null) {
                p.add(cb.equal(root.get("building").get("id"), buildingId));
            }
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("building", JoinType.LEFT);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Unit> page = unitRepository.findAll(spec, pageable);
        Map<UUID, List<Occupancy>> occupants = currentOccupants(page.getContent());
        return PaginaResponse.of(page, u -> toResponse(u, occupants.getOrDefault(u.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public UnitResponse unit(UUID condominiumId, UUID id) {
        access.require(condominiumId, Permissao.STRUCTURE_VIEW);
        Unit unit = loadUnit(condominiumId, id);
        return toResponse(unit, currentOccupants(List.of(unit)).getOrDefault(id, List.of()));
    }

    @Transactional
    public UnitResponse createUnit(UUID condominiumId, UnitRequest request) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.STRUCTURE_EDIT);
        Unit unit = new Unit(condominium.getOrganizationId(), condominiumId);
        apply(unit, condominiumId, request);
        unitRepository.save(unit);
        auditService.registrar(AcaoAuditoria.UNIT_CREATED, "Unit", unit.getId(), Map.of("unit", unit.label(),
                "idealFraction", unit.getIdealFraction().toPlainString()));
        return toResponse(unit, List.of());
    }

    @Transactional
    public UnitResponse updateUnit(UUID condominiumId, UUID id, UnitRequest request) {
        access.requireActive(condominiumId, Permissao.STRUCTURE_EDIT);
        Unit unit = loadUnit(condominiumId, id);
        if (request.version() == null || !request.version().equals(unit.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        String before = unit.label() + " / fração " + unit.getIdealFraction().stripTrailingZeros().toPlainString();
        apply(unit, condominiumId, request);
        unitRepository.flush();
        auditService.registrar(AcaoAuditoria.UNIT_UPDATED, "Unit", id, Map.of("from", before,
                "to", unit.label() + " / fração " + unit.getIdealFraction().stripTrailingZeros().toPlainString()));
        return toResponse(unit, currentOccupants(List.of(unit)).getOrDefault(id, List.of()));
    }

    @Transactional
    public UnitResponse setUnitActive(UUID condominiumId, UUID id, boolean active) {
        access.requireActive(condominiumId, Permissao.STRUCTURE_EDIT);
        Unit unit = loadUnit(condominiumId, id);
        if (unit.isActive() == active) {
            throw ApiException.regraNegocio("UNIT_STATUS_UNCHANGED", active ? "A unidade já está ativa." : "A unidade já está inativa.");
        }
        unit.setActive(active);
        unitRepository.flush();
        auditService.registrar(active ? AcaoAuditoria.UNIT_ACTIVATED : AcaoAuditoria.UNIT_DEACTIVATED, "Unit", id, Map.of("unit", unit.label()));
        return toResponse(unit, currentOccupants(List.of(unit)).getOrDefault(id, List.of()));
    }

    @Transactional(readOnly = true)
    public FractionSummary fractionSummary(UUID condominiumId) {
        access.require(condominiumId, Permissao.STRUCTURE_VIEW);
        return summarize(condominiumId);
    }

    public FractionSummary summarize(UUID condominiumId) {
        BigDecimal total = unitRepository.sumActiveFractions(condominiumId);
        long count = unitRepository.countByCondominiumIdAndActiveTrue(condominiumId);
        BigDecimal difference = HUNDRED.subtract(total);
        return new FractionSummary(total, count, difference.signum() == 0, difference);
    }

    // ---------------------------------------------------------------- importação

    /**
     * Importa unidades (e proprietários) de CSV. Colunas: bloco; unidade*; tipo; fracao; area; proprietario;
     * documento; email; telefone. Tudo ou nada: com qualquer erro, nada é gravado e o relatório lista as linhas.
     */
    @Transactional
    public ImportResult importUnits(UUID condominiumId, byte[] content, boolean dryRun) {
        Condominium condominium = access.requireActive(condominiumId, Permissao.STRUCTURE_EDIT);
        List<CsvReader.Linha> rows = CsvReader.ler(content);
        List<ImportError> errors = new ArrayList<>();
        if (!CsvReader.cabecalho(content).contains("unidade")) {
            errors.add(new ImportError(1, "Cabeçalho inválido: a coluna \"unidade\" é obrigatória."));
            return new ImportResult(dryRun, rows.size(), 0, 0, 0, 0, errors);
        }
        if (rows.size() > MAX_IMPORT_LINES) {
            errors.add(new ImportError(1, "Arquivo com mais de " + MAX_IMPORT_LINES + " linhas."));
            return new ImportResult(dryRun, rows.size(), 0, 0, 0, 0, errors);
        }

        record Parsed(int line, String building, String identifier, Unit.Type type, BigDecimal fraction,
                      BigDecimal area, String ownerName, String document, String email, String phone) {
        }
        List<Parsed> parsed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Building> buildingsByName = buildingRepository.findByCondominiumIdOrderByNameAsc(condominiumId)
                .stream().collect(Collectors.toMap(b -> b.getName().toLowerCase(Locale.ROOT), Function.identity()));

        for (CsvReader.Linha row : rows) {
            int errorsBefore = errors.size();
            String identifier = row.get("unidade");
            String buildingName = row.get("bloco");
            if (identifier == null) {
                errors.add(new ImportError(row.numero(), "Unidade não informada."));
                continue;
            }
            if (identifier.length() > 30 || (buildingName != null && buildingName.length() > 80)) {
                errors.add(new ImportError(row.numero(), "Bloco (até 80) ou unidade (até 30 caracteres) muito longos."));
            }
            Unit.Type type = Unit.Type.APARTMENT;
            if (row.get("tipo") != null) {
                type = TYPE_ALIASES.get(CsvReader.normalizarCabecalho(row.get("tipo")));
                if (type == null) {
                    errors.add(new ImportError(row.numero(), "Tipo inválido: \"" + row.get("tipo")
                            + "\" (use apartamento, casa, sala, loja, vaga ou outro)."));
                }
            }
            BigDecimal fraction = parseNumber(row.get("fracao"), row.numero(), "Fração ideal", errors);
            if (fraction != null && (fraction.signum() < 0 || fraction.compareTo(HUNDRED) > 0 || fraction.scale() > 6)) {
                errors.add(new ImportError(row.numero(), "Fração ideal deve estar entre 0 e 100, com até 6 casas decimais."));
            }
            BigDecimal area = parseNumber(row.get("area"), row.numero(), "Área", errors);
            if (area != null && area.signum() <= 0) {
                errors.add(new ImportError(row.numero(), "Área deve ser positiva."));
            }
            String key = (buildingName == null ? "" : buildingName.toLowerCase(Locale.ROOT)) + "|" + identifier.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                errors.add(new ImportError(row.numero(), "Unidade repetida no arquivo: " + identifier));
            } else if (unitExists(condominiumId, buildingName == null ? null : buildingsByName.get(buildingName.toLowerCase(Locale.ROOT)), identifier)) {
                errors.add(new ImportError(row.numero(), "Unidade já cadastrada: " + identifier));
            }
            String owner = row.get("proprietario");
            String document = row.get("documento");
            String email = row.get("email");
            String phone = row.get("telefone");
            if (owner == null && (document != null || email != null || phone != null)) {
                errors.add(new ImportError(row.numero(), "Informe o nome do proprietário."));
            }
            if (document != null && !DocumentosBrasileiros.isCpfOuCnpjValido(document)) {
                errors.add(new ImportError(row.numero(), "CPF/CNPJ inválido: " + document));
            }
            if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                errors.add(new ImportError(row.numero(), "E-mail inválido: " + email));
            }
            if (phone != null && !DocumentosBrasileiros.isTelefoneValido(phone)) {
                errors.add(new ImportError(row.numero(), "Telefone inválido: " + phone));
            }
            if (errors.size() == errorsBefore) {
                parsed.add(new Parsed(row.numero(), buildingName, identifier, type, fraction, area, owner,
                        document == null ? null : DocumentosBrasileiros.normalizarDocumento(document), email,
                        DocumentosBrasileiros.somenteDigitos(phone)));
            }
        }
        if (!errors.isEmpty() || dryRun) {
            long newBuildings = parsed.stream().map(Parsed::building).filter(b -> b != null)
                    .map(b -> b.toLowerCase(Locale.ROOT)).distinct().filter(b -> !buildingsByName.containsKey(b)).count();
            return new ImportResult(dryRun || !errors.isEmpty(), rows.size(), errors.isEmpty() ? (int) newBuildings : 0,
                    errors.isEmpty() ? parsed.size() : 0, 0, 0, errors);
        }

        int buildingsCreated = 0;
        int peopleCreated = 0;
        int peopleReused = 0;
        Map<String, Person> peopleByDocument = new HashMap<>();
        LocalDate today = LocalDate.now(clock);
        UUID actor = access.user().idUsuario();
        for (Parsed p : parsed) {
            Building building = null;
            if (p.building() != null) {
                building = buildingsByName.get(p.building().toLowerCase(Locale.ROOT));
                if (building == null) {
                    building = buildingRepository.save(new Building(condominium.getOrganizationId(), condominiumId, p.building(), null));
                    buildingsByName.put(p.building().toLowerCase(Locale.ROOT), building);
                    buildingsCreated++;
                }
            }
            Unit unit = new Unit(condominium.getOrganizationId(), condominiumId);
            unit.update(building, p.identifier(), p.type(), null, p.area(), p.fraction(), null);
            unitRepository.save(unit);
            if (p.ownerName() != null) {
                Person person = p.document() == null ? null : peopleByDocument.computeIfAbsent(p.document(),
                        d -> personRepository.findByOrganizationIdAndDocument(condominium.getOrganizationId(), d).orElse(null));
                if (person == null) {
                    person = new Person(condominium.getOrganizationId());
                    person.update(p.ownerName(), p.document() != null && p.document().length() == 14
                            ? Person.Kind.COMPANY : Person.Kind.INDIVIDUAL, p.document(), p.email(), p.phone(), null, null);
                    personRepository.save(person);
                    if (p.document() != null) {
                        peopleByDocument.put(p.document(), person);
                    }
                    peopleCreated++;
                } else {
                    peopleReused++;
                }
                occupancyRepository.save(new Occupancy(unit, person, Occupancy.Role.OWNER, true, today, actor));
            }
        }
        auditService.registrar(AcaoAuditoria.UNITS_IMPORTED, "Condominium", condominiumId, Map.of("units", parsed.size(),
                "buildingsCreated", buildingsCreated, "peopleCreated", peopleCreated));
        return new ImportResult(false, rows.size(), buildingsCreated, parsed.size(), peopleCreated, peopleReused, List.of());
    }

    // ---------------------------------------------------------------- apoio

    public Unit loadUnit(UUID condominiumId, UUID id) {
        return unitRepository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
    }

    private Building loadBuilding(UUID condominiumId, UUID id) {
        return buildingRepository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Bloco"));
    }

    private void ensureBuildingNameFree(UUID condominiumId, String name, UUID exceptId) {
        buildingRepository.findByName(condominiumId, name.trim())
                .filter(b -> !b.getId().equals(exceptId))
                .ifPresent(b -> {
                    throw ApiException.conflito("BUILDING_NAME_TAKEN", "Já existe um bloco com este nome.");
                });
    }

    private void apply(Unit unit, UUID condominiumId, UnitRequest request) {
        Building building = request.buildingId() == null ? null : loadBuilding(condominiumId, request.buildingId());
        if (building != null && !building.isActive()) {
            throw ApiException.regraNegocio("BUILDING_INACTIVE", "O bloco selecionado está inativo.");
        }
        UUID self = unit.getVersao() == null ? null : unit.getId();
        boolean duplicate = unitRepository.findByIdentifier(condominiumId, request.identifier().trim()).stream()
                .anyMatch(u -> !u.getId().equals(self) && sameBuilding(u.getBuilding(), building));
        if (duplicate) {
            throw ApiException.conflito("UNIT_IDENTIFIER_TAKEN", "Já existe uma unidade com esta identificação neste bloco.");
        }
        unit.update(building, request.identifier(), request.type(), request.floor(), request.area(),
                request.idealFraction(), request.notes());
    }

    private boolean unitExists(UUID condominiumId, Building building, String identifier) {
        return unitRepository.findByIdentifier(condominiumId, identifier).stream()
                .anyMatch(u -> sameBuilding(u.getBuilding(), building));
    }

    private static boolean sameBuilding(Building a, Building b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }

    private static BigDecimal parseNumber(String raw, int line, String label, List<ImportError> errors) {
        if (raw == null) {
            return null;
        }
        try {
            return NumerosBrasileiros.converter(raw);
        } catch (NumberFormatException e) {
            errors.add(new ImportError(line, label + " inválida: " + raw));
            return null;
        }
    }

    private Map<UUID, List<Occupancy>> currentOccupants(List<Unit> units) {
        if (units.isEmpty()) {
            return Map.of();
        }
        return occupancyRepository.findCurrentByUnits(units.stream().map(Unit::getId).toList()).stream()
                .collect(Collectors.groupingBy(o -> o.getUnit().getId()));
    }

    private static UnitResponse toResponse(Unit u, List<Occupancy> occupants) {
        String owner = occupants.stream().filter(o -> o.getRole() == Occupancy.Role.OWNER)
                .map(o -> o.getPerson().getName()).sorted().collect(Collectors.joining(", "));
        String financial = occupants.stream().filter(Occupancy::isFinancialResponsible)
                .map(o -> o.getPerson().getName()).findFirst().orElse(null);
        return new UnitResponse(u.getId(), u.getBuilding() == null ? null : u.getBuilding().getId(),
                u.getBuilding() == null ? null : u.getBuilding().getName(), u.getIdentifier(), u.label(), u.getType(),
                u.getFloor(), u.getArea(), u.getIdealFraction(), u.getNotes(), u.isActive(), u.getVersao(),
                owner.isEmpty() ? null : owner, financial);
    }
}
