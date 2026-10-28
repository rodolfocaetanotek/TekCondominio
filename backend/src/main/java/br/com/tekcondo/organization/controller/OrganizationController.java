package br.com.tekcondo.organization.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.organization.dto.AdminInviteRequest;
import br.com.tekcondo.organization.dto.OrganizationRequest;
import br.com.tekcondo.organization.dto.OrganizationUser;
import br.com.tekcondo.organization.dto.OrganizationResponse;
import br.com.tekcondo.organization.service.OrganizationService;
import br.com.tekcondo.shared.web.PageRequests;
import br.com.tekcondo.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizações (administrador da plataforma)")
public class OrganizationController {

    private static final Map<String, String> SORTS = Map.of("name", "name", "createdAt", "createdAt");

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista administradoras")
    public PageResponse<OrganizationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String sort) {
        return service.list(PageRequests.of(page, size, sort, SORTS, Sort.by("name")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma administradora")
    public OrganizationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/users")
    @Operation(summary = "Usuários da administradora")
    public List<OrganizationUser> users(@PathVariable UUID id) {
        return service.users(id);
    }

    @PostMapping("/{id}/admins")
    @Operation(summary = "Convida um administrador para a administradora (definição de senha por e-mail)")
    public ResponseEntity<OrganizationUser> inviteAdmin(@PathVariable UUID id,
                                                        @Valid @RequestBody AdminInviteRequest request) {
        return ResponseEntity.status(201).body(service.inviteAdmin(id, request.name().trim(), request.email().trim()));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Reativa a administradora")
    public OrganizationResponse activate(@PathVariable UUID id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Inativa a administradora (bloqueia o acesso de todos os seus usuários)")
    public OrganizationResponse deactivate(@PathVariable UUID id) {
        return service.setActive(id, false);
    }

    @PostMapping
    @Operation(summary = "Cadastra administradora e seu primeiro administrador")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationRequest request) {
        OrganizationResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + created.id())).body(created);
    }
}
