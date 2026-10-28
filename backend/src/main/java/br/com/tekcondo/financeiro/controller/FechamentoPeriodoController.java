package br.com.tekcondo.financeiro.controller;

import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.financeiro.service.FechamentoPeriodoService;
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
@RequestMapping("/api/v1/condominios/{idCondominio}/financeiro/fechamento")
@Tag(name = "Financeiro — fechamento mensal")
public class FechamentoPeriodoController {

    private final FechamentoPeriodoService service;

    public FechamentoPeriodoController(FechamentoPeriodoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Situação do fechamento: data fechada, sugestão do próximo fechamento e histórico")
    public FechamentoPeriodoService.SituacaoFechamento situacao(@PathVariable UUID idCondominio) {
        return service.situacao(idCondominio);
    }

    @GetMapping("/verificar")
    @Operation(summary = "Conferência antes do fechamento: pendências de conciliação/aprovação, totais e saldos")
    public FechamentoPeriodoService.Verificacao verificar(@PathVariable UUID idCondominio,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.verificar(idCondominio, ate);
    }

    @PostMapping
    @Operation(summary = "Fecha o período até o último dia do mês informado (bloqueia alterações retroativas)")
    public FechamentoPeriodoService.SituacaoFechamento fechar(@PathVariable UUID idCondominio,
                                                              @Valid @RequestBody FechamentoPeriodoService.FechamentoRequest request) {
        return service.fechar(idCondominio, request);
    }

    @PostMapping("/reabrir")
    @Operation(summary = "Reabre o período (total ou parcialmente), com justificativa")
    public FechamentoPeriodoService.SituacaoFechamento reabrir(@PathVariable UUID idCondominio,
                                                               @Valid @RequestBody FechamentoPeriodoService.ReaberturaRequest request) {
        return service.reabrir(idCondominio, request);
    }
}
