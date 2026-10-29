package br.com.tekcondo.portaria;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominios/{idCondominio}/portaria")
@Tag(name = "Portaria — visitantes, prestadores e entregas")
public class PortariaController {

    private final PortariaService service;

    public PortariaController(PortariaService service) {
        this.service = service;
    }

    @GetMapping("/hoje")
    @Operation(summary = "Painel da portaria: esperados hoje, quem está dentro e movimentação do dia")
    public List<PortariaService.AutorizacaoResponse> hoje(@PathVariable UUID idCondominio) {
        return service.hoje(idCondominio);
    }

    @GetMapping("/historico")
    @Operation(summary = "Histórico de registros (padrão: últimos 30 dias)")
    public List<PortariaService.AutorizacaoResponse> historico(@PathVariable UUID idCondominio,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.historico(idCondominio, de, ate);
    }

    @PostMapping("/chegadas")
    @Operation(summary = "Registra chegada sem pré-autorização (entrada já registrada)")
    public ResponseEntity<PortariaService.AutorizacaoResponse> registrarChegada(@PathVariable UUID idCondominio,
                                                                                @Valid @RequestBody PortariaService.AutorizacaoRequest request) {
        return ResponseEntity.status(201).body(service.registrarChegada(idCondominio, request));
    }

    @PostMapping("/entrada-por-codigo")
    @Operation(summary = "Entrada pelo código de 6 dígitos informado pelo visitante")
    public PortariaService.AutorizacaoResponse registrarEntradaPorCodigo(@PathVariable UUID idCondominio,
                                                                         @Valid @RequestBody PortariaService.CodigoRequest request) {
        return service.registrarEntradaPorCodigo(idCondominio, request.codigo());
    }

    @PostMapping("/{id}/entrada")
    public PortariaService.AutorizacaoResponse registrarEntrada(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.registrarEntrada(idCondominio, id);
    }

    @PostMapping("/{id}/saida")
    public PortariaService.AutorizacaoResponse registrarSaida(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.registrarSaida(idCondominio, id);
    }
}
