package br.com.tekcondo.supplier.controller;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.supplier.service.ContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominios/{c}/contracts")
@Tag(name = "Contratos")
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContractService.Response> list(@PathVariable UUID c) {
        return service.list(c);
    }

    @GetMapping("/{id}")
    public ContractService.Response get(@PathVariable UUID c, @PathVariable UUID id) {
        return service.get(c, id);
    }

    @PostMapping
    public ResponseEntity<ContractService.Response> create(@PathVariable UUID c, @Valid @RequestBody ContractService.Request request) {
        return ResponseEntity.status(201).body(service.save(c, null, request));
    }

    @PutMapping("/{id}")
    public ContractService.Response update(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody ContractService.Request request) {
        return service.save(c, id, request);
    }

    @PostMapping("/{id}/terminate")
    public ContractService.Response terminate(@PathVariable UUID c, @PathVariable UUID id,
                                              @Valid @RequestBody ContractService.TerminateRequest request) {
        return service.terminate(c, id, request);
    }
}
