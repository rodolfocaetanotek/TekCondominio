package br.com.tekcondo.people.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.service.CondominiumAccess;
import br.com.tekcondo.acesso.autorizacao.EscopoAcesso;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.acesso.entity.Papel;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.entity.PapelUsuario;
import br.com.tekcondo.acesso.repository.PapelRepository;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.acesso.repository.PapelUsuarioRepository;
import br.com.tekcondo.acesso.service.SenhaService;
import br.com.tekcondo.people.dto.PeopleDtos.EndOccupancyRequest;
import br.com.tekcondo.people.dto.PeopleDtos.OccupancyRequest;
import br.com.tekcondo.people.dto.PeopleDtos.OccupancyResponse;
import br.com.tekcondo.people.dto.PeopleDtos.PersonDetail;
import br.com.tekcondo.people.dto.PeopleDtos.PersonRequest;
import br.com.tekcondo.people.dto.PeopleDtos.PersonSummary;
import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.people.repository.OccupancyRepository;
import br.com.tekcondo.people.repository.PersonRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.structure.entity.Unit;
import br.com.tekcondo.structure.repository.UnitRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pessoas (cadastro único por CPF/CNPJ na administradora) e ocupações das unidades.
 * Quem não tem acesso à administradora inteira só enxerga pessoas vinculadas aos seus condomínios.
 */
@Service
public class PeopleService {

    private final PersonRepository personRepository;
    private final OccupancyRepository occupancyRepository;
    private final UnitRepository unitRepository;
    private final UsuarioRepository userRepository;
    private final PapelRepository roleRepository;
    private final PapelUsuarioRepository userRoleRepository;
    private final SenhaService passwordService;
    private final AcessoService accessService;
    private final CondominiumAccess condominiumAccess;
    private final AuditoriaService auditService;

    public PeopleService(PersonRepository personRepository, OccupancyRepository occupancyRepository,
                         UnitRepository unitRepository, UsuarioRepository userRepository, PapelRepository roleRepository,
                         PapelUsuarioRepository userRoleRepository, SenhaService passwordService,
                         AcessoService accessService, CondominiumAccess condominiumAccess, AuditoriaService auditService) {
        this.personRepository = personRepository;
        this.occupancyRepository = occupancyRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordService = passwordService;
        this.accessService = accessService;
        this.condominiumAccess = condominiumAccess;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------------- pessoas

    @Transactional(readOnly = true)
    public PaginaResponse<PersonSummary> list(String search, Boolean active, UUID condominiumId, Pageable pageable) {
        UUID orgId = condominiumAccess.organizationId();
        EscopoAcesso scope = accessService.exigirEscopo(Permissao.PEOPLE_VIEW);
        if (condominiumId != null) {
            condominiumAccess.require(condominiumId, Permissao.PEOPLE_VIEW);
        }
        Specification<Person> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));
            if (search != null && !search.isBlank()) {
                String term = search.trim().toLowerCase(Locale.ROOT);
                String digits = term.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("name")), "%" + term + "%"));
                or.add(cb.like(cb.lower(root.get("email")), "%" + term + "%"));
                if (!digits.isEmpty()) {
                    or.add(cb.like(root.get("document"), "%" + digits + "%"));
                }
                p.add(cb.or(or.toArray(Predicate[]::new)));
            }
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            Set<UUID> condos = condominiumId != null ? Set.of(condominiumId)
                    : scope.administradoraInteira() ? null : scope.idsCondominio();
            if (condos != null) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<Occupancy> o = sub.from(Occupancy.class);
                sub.select(o.get("person").get("id"))
                        .where(o.get("unit").get("condominiumId").in(condos));
                p.add(root.get("id").in(sub));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Person> page = personRepository.findAll(spec, pageable);
        Map<UUID, List<String>> units = new java.util.HashMap<>();
        for (Person person : page.getContent()) {
            units.put(person.getId(), occupancyRepository.findByPerson(person.getId()).stream()
                    .filter(Occupancy::isCurrent).map(o -> o.getUnit().label() + " (" + roleLabel(o.getRole()) + ")")
                    .distinct().toList());
        }
        return PaginaResponse.of(page, x -> new PersonSummary(x.getId(), x.getName(), x.getKind(), x.getDocument(),
                x.getEmail(), x.getPhone(), x.isActive(), units.getOrDefault(x.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PersonDetail get(UUID id) {
        return toDetail(loadVisible(id, Permissao.PEOPLE_VIEW));
    }

    @Transactional
    public PersonDetail create(PersonRequest request) {
        accessService.exigirEscopo(Permissao.PEOPLE_EDIT);
        Person person = new Person(condominiumAccess.organizationId());
        apply(person, request);
        personRepository.save(person);
        auditService.registrar(AcaoAuditoria.PERSON_CREATED, "Person", person.getId(), Map.of("name", person.getName()));
        return toDetail(person);
    }

    @Transactional
    public PersonDetail update(UUID id, PersonRequest request) {
        Person person = loadVisible(id, Permissao.PEOPLE_EDIT);
        if (request.version() == null || !request.version().equals(person.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        String before = person.getName() + " / " + person.getEmail() + " / " + person.getPhone();
        apply(person, request);
        personRepository.flush();
        auditService.registrar(AcaoAuditoria.PERSON_UPDATED, "Person", id,
                Map.of("from", before, "to", person.getName() + " / " + person.getEmail() + " / " + person.getPhone()));
        return toDetail(person);
    }

    @Transactional
    public PersonDetail setActive(UUID id, boolean active) {
        Person person = loadVisible(id, Permissao.PEOPLE_EDIT);
        if (!active && occupancyRepository.findByPerson(id).stream().anyMatch(Occupancy::isCurrent)) {
            throw ApiException.regraNegocio("PERSON_HAS_OCCUPANCY",
                    "A pessoa possui vínculos vigentes com unidades. Encerre os vínculos antes de inativá-la.");
        }
        person.setActive(active);
        personRepository.flush();
        auditService.registrar(active ? AcaoAuditoria.PERSON_ACTIVATED : AcaoAuditoria.PERSON_DEACTIVATED, "Person", id, null);
        return toDetail(person);
    }

    /**
     * Concede acesso ao portal do morador: cria (ou vincula) o usuário com o e-mail da pessoa e atribui, em cada
     * condomínio onde ela tem vínculo vigente, o papel de Proprietário ou Morador. Envia convite para definir a senha.
     */
    @Transactional
    public PersonDetail grantPortalAccess(UUID id) {
        UsuarioAtual actor = condominiumAccess.user();
        accessService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Person person = loadVisible(id, Permissao.PEOPLE_VIEW);
        if (person.getEmail() == null) {
            throw ApiException.regraNegocio("PERSON_WITHOUT_EMAIL", "Cadastre o e-mail da pessoa para conceder acesso ao portal.");
        }
        List<Occupancy> current = occupancyRepository.findByPerson(id).stream().filter(Occupancy::isCurrent).toList();
        if (current.isEmpty()) {
            throw ApiException.regraNegocio("PERSON_WITHOUT_OCCUPANCY", "A pessoa não possui vínculo vigente com nenhuma unidade.");
        }
        Usuario user = userRepository.findByIdPessoa(id).orElse(null);
        boolean created = false;
        if (user == null) {
            Usuario byEmail = userRepository.buscarPorEmail(person.getEmail()).orElse(null);
            if (byEmail != null) {
                if (!actor.idAdministradora().equals(byEmail.getIdAdministradora()) || byEmail.getIdPessoa() != null) {
                    throw ApiException.conflito("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
                }
                user = byEmail;
            } else {
                user = userRepository.save(Usuario.membroAdministradora(actor.idAdministradora(), person.getName(),
                        person.getEmail(), passwordService.hashSenhaInutilizavel()));
                created = true;
            }
            user.vincularPessoa(id);
        }
        Papel owner = roleRepository.buscarPapelSistema("OWNER").orElseThrow();
        Papel resident = roleRepository.buscarPapelSistema("RESIDENT").orElseThrow();
        Map<UUID, Boolean> ownsInCondo = current.stream().collect(Collectors.toMap(o -> o.getUnit().getCondominiumId(),
                o -> o.getRole() == Occupancy.Role.OWNER, Boolean::logicalOr));
        for (Map.Entry<UUID, Boolean> e : ownsInCondo.entrySet()) {
            Papel role = e.getValue() ? owner : resident;
            if (!userRoleRepository.existeNoCondominio(user.getId(), role.getId(), e.getKey())) {
                userRoleRepository.save(new PapelUsuario(user.getId(), role, actor.idAdministradora(), e.getKey(), actor.idUsuario()));
            }
        }
        auditService.registrar(AcaoAuditoria.PORTAL_ACCESS_GRANTED, "Person", id,
                Map.of("email", person.getEmail(), "condominiums", ownsInCondo.size()));
        if (created || user.nuncaAcessou()) {
            passwordService.enviarConvite(user);
        }
        return toDetail(person);
    }

    // ---------------------------------------------------------------- ocupações

    @Transactional(readOnly = true)
    public List<OccupancyResponse> unitOccupancies(UUID condominiumId, UUID unitId) {
        condominiumAccess.require(condominiumId, Permissao.PEOPLE_VIEW);
        unitRepository.findByIdAndCondominiumId(unitId, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        return occupancyRepository.findByUnit(unitId).stream().map(OccupancyResponse::from).toList();
    }

    @Transactional
    public OccupancyResponse addOccupancy(UUID condominiumId, OccupancyRequest request) {
        condominiumAccess.requireActive(condominiumId, Permissao.PEOPLE_EDIT);
        Unit unit = unitRepository.findByIdAndCondominiumId(request.unitId(), condominiumId)
                .orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        Person person = personRepository.findByIdAndOrganizationId(request.personId(), condominiumAccess.organizationId())
                .orElseThrow(() -> ApiException.naoEncontrado("Pessoa"));
        if (!person.isActive() || !unit.isActive()) {
            throw ApiException.regraNegocio("INACTIVE_RECORD", "Pessoa ou unidade inativa.");
        }
        if (occupancyRepository.existsCurrent(unit.getId(), person.getId(), request.role())) {
            throw ApiException.conflito("OCCUPANCY_EXISTS", "Esta pessoa já possui este vínculo vigente com a unidade.");
        }
        if (request.financialResponsible() && occupancyRepository.findCurrentFinancialResponsible(unit.getId()).isPresent()) {
            throw ApiException.conflito("FINANCIAL_RESPONSIBLE_EXISTS",
                    "A unidade já possui responsável financeiro. Use \"Definir como responsável financeiro\" para transferir.");
        }
        Occupancy occupancy = occupancyRepository.save(new Occupancy(unit, person, request.role(),
                request.financialResponsible(), request.startDate(), condominiumAccess.user().idUsuario()));
        auditService.registrar(AcaoAuditoria.OCCUPANCY_STARTED, "Unit", unit.getId(), Map.of("person", person.getName(),
                "role", request.role().name(), "financialResponsible", request.financialResponsible()));
        return OccupancyResponse.from(occupancy);
    }

    @Transactional
    public OccupancyResponse endOccupancy(UUID condominiumId, UUID occupancyId, EndOccupancyRequest request) {
        condominiumAccess.requireActive(condominiumId, Permissao.PEOPLE_EDIT);
        Occupancy occupancy = loadOccupancy(condominiumId, occupancyId);
        occupancy.end(request.endDate(), request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        occupancyRepository.flush();
        auditService.registrar(AcaoAuditoria.OCCUPANCY_ENDED, "Unit", occupancy.getUnit().getId(), Map.of(
                "person", occupancy.getPerson().getName(), "role", occupancy.getRole().name(),
                "endDate", request.endDate().toString()));
        return OccupancyResponse.from(occupancy);
    }

    /** Transfere a responsabilidade financeira da unidade para este vínculo vigente. */
    @Transactional
    public OccupancyResponse makeFinancialResponsible(UUID condominiumId, UUID occupancyId) {
        condominiumAccess.requireActive(condominiumId, Permissao.PEOPLE_EDIT);
        Occupancy occupancy = loadOccupancy(condominiumId, occupancyId);
        if (!occupancy.isCurrent()) {
            throw ApiException.regraNegocio("OCCUPANCY_ENDED", "O vínculo está encerrado.");
        }
        occupancyRepository.findCurrentFinancialResponsible(occupancy.getUnit().getId())
                .filter(o -> !o.getId().equals(occupancyId))
                .ifPresent(previous -> {
                    previous.setFinancialResponsible(false);
                    occupancyRepository.flush();
                });
        occupancy.setFinancialResponsible(true);
        occupancyRepository.flush();
        auditService.registrar(AcaoAuditoria.FINANCIAL_RESPONSIBLE_CHANGED, "Unit", occupancy.getUnit().getId(),
                Map.of("person", occupancy.getPerson().getName()));
        return OccupancyResponse.from(occupancy);
    }

    // ---------------------------------------------------------------- apoio

    private Occupancy loadOccupancy(UUID condominiumId, UUID occupancyId) {
        return occupancyRepository.findById(occupancyId)
                .filter(o -> o.getOrganizationId().equals(condominiumAccess.organizationId())
                        && o.getUnit().getCondominiumId().equals(condominiumId))
                .orElseThrow(() -> ApiException.naoEncontrado("Vínculo"));
    }

    private Person loadVisible(UUID id, Permissao permission) {
        Person person = personRepository.findByIdAndOrganizationId(id, condominiumAccess.organizationId())
                .orElseThrow(() -> ApiException.naoEncontrado("Pessoa"));
        EscopoAcesso scope = accessService.exigirEscopo(permission);
        if (!scope.administradoraInteira()) {
            boolean visible = occupancyRepository.findByPerson(id).stream()
                    .anyMatch(o -> scope.idsCondominio().contains(o.getUnit().getCondominiumId()));
            if (!visible) {
                throw ApiException.naoEncontrado("Pessoa");
            }
        }
        return person;
    }

    private void apply(Person person, PersonRequest request) {
        String document = DocumentosBrasileiros.normalizarDocumento(request.document());
        if (document != null) {
            boolean company = request.kind() == Person.Kind.COMPANY;
            if (company != (document.length() == 14)) {
                throw ApiException.regraNegocio("DOCUMENT_KIND_MISMATCH",
                        company ? "Pessoa jurídica deve ter CNPJ." : "Pessoa física deve ter CPF.");
            }
            personRepository.findByOrganizationIdAndDocument(person.getOrganizationId(), document)
                    .filter(other -> !other.getId().equals(person.getId()))
                    .ifPresent(other -> {
                        throw ApiException.conflito("PERSON_DOCUMENT_TAKEN",
                                "Já existe uma pessoa com este CPF/CNPJ: " + other.getName() + ".");
                    });
        }
        person.update(request.name(), request.kind(), document, blank(request.email()),
                DocumentosBrasileiros.somenteDigitos(request.phone()), DocumentosBrasileiros.somenteDigitos(request.secondaryPhone()),
                blank(request.notes()));
    }

    private PersonDetail toDetail(Person p) {
        List<OccupancyResponse> occupancies = occupancyRepository.findByPerson(p.getId()).stream()
                .map(OccupancyResponse::from).toList();
        return new PersonDetail(p.getId(), p.getName(), p.getKind(), p.getDocument(), p.getEmail(), p.getPhone(),
                p.getSecondaryPhone(), p.getNotes(), p.isActive(), p.getVersao(),
                userRepository.findByIdPessoa(p.getId()).isPresent(), occupancies);
    }

    private static String roleLabel(Occupancy.Role role) {
        return switch (role) {
            case OWNER -> "proprietário";
            case TENANT -> "inquilino";
            case RESIDENT -> "morador";
        };
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
