package br.com.tekcondo.finance.controller;

import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.finance.service.PeriodClosingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominiums/{c}/finance/closing")
@Tag(name = "Financeiro — fechamento mensal")
public class PeriodClosingController {

    private final PeriodClosingService service;

    public PeriodClosingController(PeriodClosingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Situação do fechamento: data fechada, sugestão do próximo fechamento e histórico")
    public PeriodClosingService.Status status(@PathVariable UUID c) {
        return service.status(c);
    }

    @GetMapping("/check")
    @Operation(summary = "Conferência antes do fechamento: pendências de conciliação/aprovação, totais e saldos")
    public PeriodClosingService.ClosingCheck check(@PathVariable UUID c,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return service.check(c, until);
    }

    @PostMapping
    @Operation(summary = "Fecha o período até o último dia do mês informado (bloqueia alterações retroativas)")
    public PeriodClosingService.Status close(@PathVariable UUID c, @Valid @RequestBody PeriodClosingService.CloseRequest request) {
        return service.close(c, request);
    }

    @PostMapping("/reopen")
    @Operation(summary = "Reabre o período (total ou parcialmente), com justificativa")
    public PeriodClosingService.Status reopen(@PathVariable UUID c, @Valid @RequestBody PeriodClosingService.ReopenRequest request) {
        return service.reopen(c, request);
    }
}
