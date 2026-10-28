package br.com.tekcondo.indicador;

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
@RequestMapping("/api/v1/condominios/{idCondominio}/indicadores")
@Tag(name = "Indicadores e alertas financeiros")
public class IndicadorController {

    private final IndicadorService service;

    public IndicadorController(IndicadorService service) {
        this.service = service;
    }

    @GetMapping("/projecao-caixa")
    @Operation(summary = "Projeção semanal de caixa pelos vencimentos em aberto (até 26 semanas)")
    public IndicadorService.ProjecaoCaixa projecaoCaixa(@PathVariable UUID idCondominio,
                                                        @RequestParam(defaultValue = "13") int semanas) {
        return service.projecaoCaixa(idCondominio, semanas);
    }

    @GetMapping("/despesas-fora-do-padrao")
    @Operation(summary = "Despesas pagas fora do padrão histórico da conta (últimos meses encerrados)")
    public List<IndicadorService.DespesaForaDoPadrao> despesasForaDoPadrao(@PathVariable UUID idCondominio,
                                                                           @RequestParam(defaultValue = "3") int meses) {
        return service.despesasForaDoPadrao(idCondominio, meses);
    }
}
