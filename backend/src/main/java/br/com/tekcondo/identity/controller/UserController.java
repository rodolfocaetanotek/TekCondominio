package br.com.tekcondo.identity.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.identity.dto.UserDtos;
import br.com.tekcondo.identity.service.UserManagementService;
import br.com.tekcondo.shared.web.PageRequests;
import br.com.tekcondo.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "Usuários e papéis")
public class UserController {

    private static final Map<String, String> SORTS = Map.of("name", "name", "email", "email", "lastLoginAt", "lastLoginAt");

    private final UserManagementService service;

    public UserController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @Operation(summary = "Lista usuários da administradora")
    public PageResponse<UserDtos.Summary> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort) {
        return service.list(search, active, PageRequests.of(page, size, sort, SORTS, Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Consulta usuário com seus papéis")
    public UserDtos.Detail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/users")
    @Operation(summary = "Cadastra usuário e envia convite por e-mail para definição da senha")
    public ResponseEntity<UserDtos.Detail> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        UserDtos.Detail created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Atualiza o nome do usuário")
    public UserDtos.Detail update(@PathVariable UUID id, @Valid @RequestBody UserDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/users/{id}/deactivate")
    @Operation(summary = "Desativa o usuário e encerra suas sessões")
    public UserDtos.Detail deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PostMapping("/users/{id}/activate")
    @Operation(summary = "Reativa o usuário")
    public UserDtos.Detail activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/users/{id}/assignments")
    @Operation(summary = "Atribui papel (para toda a administradora ou para um condomínio)")
    public UserDtos.Detail addAssignment(@PathVariable UUID id, @Valid @RequestBody UserDtos.AssignmentRequest request) {
        return service.addAssignment(id, request);
    }

    @DeleteMapping("/users/{id}/assignments/{assignmentId}")
    @Operation(summary = "Remove uma atribuição de papel")
    public UserDtos.Detail removeAssignment(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        return service.removeAssignment(id, assignmentId);
    }

    @PostMapping("/users/{id}/invite")
    @Operation(summary = "Reenvia o convite (somente para quem ainda não acessou)")
    public ResponseEntity<Void> resendInvite(@PathVariable UUID id) {
        service.resendInvite(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/roles")
    @Operation(summary = "Papéis disponíveis, com escopo e permissões")
    public List<UserDtos.RoleResponse> roles() {
        return service.roles();
    }
}
