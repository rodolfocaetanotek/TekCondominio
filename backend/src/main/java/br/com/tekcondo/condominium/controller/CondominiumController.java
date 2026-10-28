package br.com.tekcondo.condominium.controller;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.condominium.dto.CondominiumRequest;
import br.com.tekcondo.condominium.dto.CondominiumResponse;
import br.com.tekcondo.condominium.dto.CondominiumSummary;
import br.com.tekcondo.condominium.dto.InactivationRequest;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.service.CondominiumService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominiums")
@Tag(name = "Condomínios")
public class CondominiumController {

    private static final Map<String, String> SORTS = Map.of(
            "name", "name", "city", "address.city", "status", "status", "createdAt", "createdAt");

    private final CondominiumService service;

    public CondominiumController(CondominiumService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista condomínios visíveis ao usuário", description = "Busca por nome, razão social ou CNPJ. Ordenação: name, city, status, createdAt.")
    public PageResponse<CondominiumSummary> list(@RequestParam(required = false) String search,
                                                 @RequestParam(required = false) CondominiumStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String sort) {
        return service.list(search, status, PageRequests.of(page, size, sort, SORTS, Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/accessible")
    @Operation(summary = "Condomínios acessíveis ao usuário, com permissões (seletor de condomínio)")
    public java.util.List<br.com.tekcondo.condominium.dto.CondominiumOption> accessible() {
        return service.accessible();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um condomínio")
    public CondominiumResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra um condomínio")
    public ResponseEntity<CondominiumResponse> create(@Valid @RequestBody CondominiumRequest request) {
        CondominiumResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/condominiums/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados cadastrais (exige a versão atual do registro)")
    public CondominiumResponse update(@PathVariable UUID id, @Valid @RequestBody CondominiumRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/inactivate")
    @Operation(summary = "Inativa o condomínio com justificativa (histórico é preservado)")
    public CondominiumResponse inactivate(@PathVariable UUID id, @Valid @RequestBody InactivationRequest request) {
        return service.inactivate(id, request.reason());
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Reativa o condomínio")
    public CondominiumResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }
}
