package br.com.tekcondo.fornecedor.controller;

import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.fornecedor.dto.FornecedorDtos.FornecedorRequest;
import br.com.tekcondo.fornecedor.dto.FornecedorDtos.FornecedorResponse;
import br.com.tekcondo.fornecedor.service.FornecedorService;
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
@RequestMapping("/api/v1/fornecedores")
@Tag(name = "Fornecedores")
public class FornecedorController {

    private final FornecedorService service;

    public FornecedorController(FornecedorService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<FornecedorResponse> listar(@RequestParam(required = false) String busca,
                                                     @RequestParam(required = false) Boolean ativo,
                                                     @RequestParam(defaultValue = "0") int pagina,
                                                     @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(busca, ativo, PaginaRequests.of(pagina, tamanho, null, Map.of(), Sort.by("nome").and(Sort.by("id"))));
    }

    @GetMapping("/{id}")
    public FornecedorResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<FornecedorResponse> cadastrar(@Valid @RequestBody FornecedorRequest request) {
        return ResponseEntity.status(201).body(service.salvar(null, request, null));
    }

    @PutMapping("/{id}")
    public FornecedorResponse atualizar(@PathVariable UUID id, @Valid @RequestBody FornecedorRequest request,
                                        @RequestParam(required = false) Boolean ativo) {
        return service.salvar(id, request, ativo);
    }
}
