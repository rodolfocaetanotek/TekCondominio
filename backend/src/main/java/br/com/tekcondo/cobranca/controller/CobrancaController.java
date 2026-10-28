package br.com.tekcondo.cobranca.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.cobranca.dto.CobrancaDtos.AcordoRequest;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.AcordoResponse;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.DebitosUnidade;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.Inadimplencia;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.NotaCobrancaRequest;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.NotaCobrancaResponse;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.RateioRequest;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.RateioResponse;
import br.com.tekcondo.cobranca.dto.CobrancaDtos.SimulacaoRateio;
import br.com.tekcondo.cobranca.service.InadimplenciaService;
import br.com.tekcondo.cobranca.service.RateioService;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.MotivoRequest;
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
@RequestMapping("/api/v1/condominios/{idCondominio}/cobranca")
@Tag(name = "Cobrança e inadimplência")
public class CobrancaController {

    private final RateioService rateios;
    private final InadimplenciaService inadimplencia;

    public CobrancaController(RateioService rateios, InadimplenciaService inadimplencia) {
        this.rateios = rateios;
        this.inadimplencia = inadimplencia;
    }

    @GetMapping("/rateios")
    @Operation(summary = "Rateios / cobranças geradas por competência")
    public List<RateioResponse> rateios(@PathVariable UUID idCondominio) {
        return rateios.listar(idCondominio);
    }

    @PostMapping("/rateios/simular")
    @Operation(summary = "Simula o rateio (valor por unidade) sem gravar")
    public SimulacaoRateio simularRateio(@PathVariable UUID idCondominio, @Valid @RequestBody RateioRequest request) {
        return rateios.simular(idCondominio, request);
    }

    @PostMapping("/rateios")
    @Operation(summary = "Gera as cobranças da competência (uma por unidade ativa)")
    public ResponseEntity<RateioResponse> gerarRateio(@PathVariable UUID idCondominio, @Valid @RequestBody RateioRequest request) {
        return ResponseEntity.status(201).body(rateios.gerar(idCondominio, request));
    }

    @PostMapping("/rateios/{id}/cancelar")
    public RateioResponse cancelarRateio(@PathVariable UUID idCondominio, @PathVariable UUID id, @Valid @RequestBody MotivoRequest request) {
        return rateios.cancelar(idCondominio, id, request.motivo());
    }

    @GetMapping("/inadimplencia")
    @Operation(summary = "Unidades inadimplentes com valores atualizados (multa e juros) até a data")
    public Inadimplencia inadimplencia(@PathVariable UUID idCondominio,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return inadimplencia.inadimplencia(idCondominio, dataBase);
    }

    @GetMapping("/unidades/{idUnidade}/debitos")
    @Operation(summary = "Demonstrativo de débitos da unidade, histórico de cobrança e acordos")
    public DebitosUnidade debitosUnidade(@PathVariable UUID idCondominio, @PathVariable UUID idUnidade,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return inadimplencia.debitosUnidade(idCondominio, idUnidade, dataBase);
    }

    @PostMapping("/unidades/{idUnidade}/notas")
    @Operation(summary = "Registra contato de cobrança")
    public ResponseEntity<NotaCobrancaResponse> registrarNota(@PathVariable UUID idCondominio, @PathVariable UUID idUnidade,
                                                              @Valid @RequestBody NotaCobrancaRequest request) {
        return ResponseEntity.status(201).body(inadimplencia.registrarNota(idCondominio, idUnidade, request));
    }

    @GetMapping("/acordos")
    public List<AcordoResponse> acordos(@PathVariable UUID idCondominio) {
        return inadimplencia.acordos(idCondominio);
    }

    @PostMapping("/acordos")
    @Operation(summary = "Cria acordo de parcelamento (renegocia os débitos selecionados)")
    public ResponseEntity<AcordoResponse> cadastrarAcordo(@PathVariable UUID idCondominio, @Valid @RequestBody AcordoRequest request) {
        return ResponseEntity.status(201).body(inadimplencia.cadastrarAcordo(idCondominio, request));
    }

    @PostMapping("/acordos/{id}/cancelar")
    public AcordoResponse cancelarAcordo(@PathVariable UUID idCondominio, @PathVariable UUID id, @Valid @RequestBody MotivoRequest request) {
        return inadimplencia.cancelarAcordo(idCondominio, id, request.motivo());
    }
}
