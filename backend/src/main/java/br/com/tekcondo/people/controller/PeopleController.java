package br.com.tekcondo.people.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.people.dto.PeopleDtos.EndOccupancyRequest;
import br.com.tekcondo.people.dto.PeopleDtos.OccupancyRequest;
import br.com.tekcondo.people.dto.PeopleDtos.OccupancyResponse;
import br.com.tekcondo.people.dto.PeopleDtos.PersonDetail;
import br.com.tekcondo.people.dto.PeopleDtos.PersonRequest;
import br.com.tekcondo.people.dto.PeopleDtos.PersonSummary;
import br.com.tekcondo.people.service.PeopleService;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Pessoas e ocupações")
public class PeopleController {

    private static final Map<String, String> SORTS = Map.of("name", "name", "createdAt", "criadoEm");

    private final PeopleService service;

    public PeopleController(PeopleService service) {
        this.service = service;
    }

    @GetMapping("/people")
    @Operation(summary = "Lista pessoas (busca por nome, e-mail ou CPF/CNPJ)")
    public PaginaResponse<PersonSummary> list(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(required = false) UUID condominiumId,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "20") int tamanho,
                                            @RequestParam(required = false) String ordem) {
        return service.list(search, active, condominiumId,
                PaginaRequests.of(pagina, tamanho, ordem, SORTS, Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/people/{id}")
    public PersonDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/people")
    public ResponseEntity<PersonDetail> create(@Valid @RequestBody PersonRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @PutMapping("/people/{id}")
    public PersonDetail update(@PathVariable UUID id, @Valid @RequestBody PersonRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/people/{id}/activate")
    public PersonDetail activate(@PathVariable UUID id) {
        return service.setActive(id, true);
    }

    @PostMapping("/people/{id}/deactivate")
    public PersonDetail deactivate(@PathVariable UUID id) {
        return service.setActive(id, false);
    }

    @PostMapping("/people/{id}/portal-access")
    @Operation(summary = "Concede acesso ao portal do morador (convite por e-mail)")
    public PersonDetail portalAccess(@PathVariable UUID id) {
        return service.grantPortalAccess(id);
    }

    @GetMapping("/condominiums/{condominiumId}/units/{unitId}/occupancies")
    @Operation(summary = "Histórico de ocupação da unidade")
    public List<OccupancyResponse> unitOccupancies(@PathVariable UUID condominiumId, @PathVariable UUID unitId) {
        return service.unitOccupancies(condominiumId, unitId);
    }

    @PostMapping("/condominiums/{condominiumId}/occupancies")
    @Operation(summary = "Vincula pessoa a unidade (proprietário, inquilino ou morador)")
    public ResponseEntity<OccupancyResponse> addOccupancy(@PathVariable UUID condominiumId,
                                                          @Valid @RequestBody OccupancyRequest request) {
        return ResponseEntity.status(201).body(service.addOccupancy(condominiumId, request));
    }

    @PostMapping("/condominiums/{condominiumId}/occupancies/{id}/end")
    @Operation(summary = "Encerra o vínculo (preserva o histórico)")
    public OccupancyResponse endOccupancy(@PathVariable UUID condominiumId, @PathVariable UUID id,
                                          @Valid @RequestBody EndOccupancyRequest request) {
        return service.endOccupancy(condominiumId, id, request);
    }

    @PostMapping("/condominiums/{condominiumId}/occupancies/{id}/financial-responsible")
    @Operation(summary = "Define este vínculo como responsável financeiro da unidade")
    public OccupancyResponse makeFinancialResponsible(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        return service.makeFinancialResponsible(condominiumId, id);
    }
}
