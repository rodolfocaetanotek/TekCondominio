package br.com.tekcondo.insight;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominiums/{c}/insights")
@Tag(name = "Indicadores e alertas financeiros")
public class InsightController {

    private final InsightService service;

    public InsightController(InsightService service) {
        this.service = service;
    }

    @GetMapping("/cash-projection")
    @Operation(summary = "Projeção semanal de caixa pelos vencimentos em aberto (até 26 semanas)")
    public InsightService.CashProjection cashProjection(@PathVariable UUID c, @RequestParam(defaultValue = "13") int weeks) {
        return service.cashProjection(c, weeks);
    }

    @GetMapping("/expense-anomalies")
    @Operation(summary = "Despesas pagas fora do padrão histórico da conta (últimos meses encerrados)")
    public List<InsightService.ExpenseAnomaly> expenseAnomalies(@PathVariable UUID c, @RequestParam(defaultValue = "3") int months) {
        return service.expenseAnomalies(c, months);
    }
}
