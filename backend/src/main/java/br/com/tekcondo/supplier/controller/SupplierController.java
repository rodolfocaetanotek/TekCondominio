package br.com.tekcondo.supplier.controller;

import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.supplier.dto.SupplierDtos.SupplierRequest;
import br.com.tekcondo.supplier.dto.SupplierDtos.SupplierResponse;
import br.com.tekcondo.supplier.service.SupplierService;
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
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Fornecedores")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<SupplierResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamanho) {
        return service.list(search, active, PaginaRequests.of(pagina, tamanho, null, Map.of(), Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(201).body(service.save(null, request, null));
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request,
                                   @RequestParam(required = false) Boolean active) {
        return service.save(id, request, active);
    }
}
