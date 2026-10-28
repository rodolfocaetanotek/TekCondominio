package br.com.tekcondo.visitor;

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
@RequestMapping("/api/v1/condominiums/{c}/visitors")
@Tag(name = "Portaria — visitantes, prestadores e entregas")
public class VisitorController {

    private final VisitorService service;

    public VisitorController(VisitorService service) {
        this.service = service;
    }

    @GetMapping("/today")
    @Operation(summary = "Painel da portaria: esperados hoje, quem está dentro e movimentação do dia")
    public List<VisitorService.PassResponse> today(@PathVariable UUID c) {
        return service.today(c);
    }

    @GetMapping
    @Operation(summary = "Histórico de registros (padrão: últimos 30 dias)")
    public List<VisitorService.PassResponse> history(@PathVariable UUID c,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(c, from, to);
    }

    @PostMapping("/arrivals")
    @Operation(summary = "Registra chegada sem pré-autorização (entrada já registrada)")
    public ResponseEntity<VisitorService.PassResponse> arrival(@PathVariable UUID c, @Valid @RequestBody VisitorService.PassRequest request) {
        return ResponseEntity.status(201).body(service.registerArrival(c, request));
    }

    @PostMapping("/check-in-code")
    @Operation(summary = "Entrada pelo código de 6 dígitos informado pelo visitante")
    public VisitorService.PassResponse checkInByCode(@PathVariable UUID c, @Valid @RequestBody VisitorService.CodeRequest request) {
        return service.checkInByCode(c, request.code());
    }

    @PostMapping("/{id}/check-in")
    public VisitorService.PassResponse checkIn(@PathVariable UUID c, @PathVariable UUID id) {
        return service.checkIn(c, id);
    }

    @PostMapping("/{id}/check-out")
    public VisitorService.PassResponse checkOut(@PathVariable UUID c, @PathVariable UUID id) {
        return service.checkOut(c, id);
    }
}
