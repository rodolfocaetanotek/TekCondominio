package br.com.tekcondo.fornecedor.controller;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.fornecedor.service.ContratoService;
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
@RequestMapping("/api/v1/condominios/{idCondominio}/contratos")
@Tag(name = "Contratos")
public class ContratoController {

    private final ContratoService service;

    public ContratoController(ContratoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContratoService.ContratoResponse> listar(@PathVariable UUID idCondominio) {
        return service.listar(idCondominio);
    }

    @GetMapping("/{id}")
    public ContratoService.ContratoResponse buscar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.buscar(idCondominio, id);
    }

    @PostMapping
    public ResponseEntity<ContratoService.ContratoResponse> cadastrar(@PathVariable UUID idCondominio,
                                                                      @Valid @RequestBody ContratoService.ContratoRequest request) {
        return ResponseEntity.status(201).body(service.salvar(idCondominio, null, request));
    }

    @PutMapping("/{id}")
    public ContratoService.ContratoResponse atualizar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                      @Valid @RequestBody ContratoService.ContratoRequest request) {
        return service.salvar(idCondominio, id, request);
    }

    @PostMapping("/{id}/encerrar")
    public ContratoService.ContratoResponse encerrar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                     @Valid @RequestBody ContratoService.EncerramentoRequest request) {
        return service.encerrar(idCondominio, id, request);
    }
}
