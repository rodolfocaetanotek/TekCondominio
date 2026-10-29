package br.com.tekcondo.reserva;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominios/{idCondominio}")
@Tag(name = "Reservas de áreas comuns")
public class ReservaController {

    private final ReservaService service;

    public ReservaController(ReservaService service) {
        this.service = service;
    }

    @GetMapping("/areas-comuns")
    public List<ReservaService.AreaComumResponse> listarAreas(@PathVariable UUID idCondominio) {
        return service.listarAreas(idCondominio);
    }

    @PostMapping("/areas-comuns")
    public ResponseEntity<ReservaService.AreaComumResponse> cadastrarArea(@PathVariable UUID idCondominio,
                                                                         @Valid @RequestBody ReservaService.AreaComumRequest request) {
        return ResponseEntity.status(201).body(service.salvarArea(idCondominio, null, request));
    }

    @PutMapping("/areas-comuns/{id}")
    public ReservaService.AreaComumResponse atualizarArea(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                         @Valid @RequestBody ReservaService.AreaComumRequest request) {
        return service.salvarArea(idCondominio, id, request);
    }

    @GetMapping("/reservas")
    @Operation(summary = "Reservas do período (padrão: últimos 7 dias e próximos 60)")
    public List<ReservaService.ReservaResponse> listar(@PathVariable UUID idCondominio, @RequestParam(required = false) UUID idArea,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listar(idCondominio, idArea, de, ate);
    }

    @PostMapping("/reservas")
    @Operation(summary = "Reserva lançada pela administração (confirmada direto; gera a taxa de uso, se houver)")
    public ResponseEntity<ReservaService.ReservaResponse> cadastrar(@PathVariable UUID idCondominio,
                                                                    @Valid @RequestBody ReservaService.ReservaRequest request) {
        return ResponseEntity.status(201).body(service.cadastrar(idCondominio, request));
    }

    @PostMapping("/reservas/{id}/aprovar")
    public ReservaService.ReservaResponse aprovar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                  @Valid @RequestBody(required = false) ReservaService.DecisaoRequest request) {
        return service.decidir(idCondominio, id, true, request == null ? null : request.observacao());
    }

    @PostMapping("/reservas/{id}/recusar")
    public ReservaService.ReservaResponse recusar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                  @Valid @RequestBody ReservaService.DecisaoRequest request) {
        return service.decidir(idCondominio, id, false, request.observacao());
    }

    @PostMapping("/reservas/{id}/cancelar")
    public ReservaService.ReservaResponse cancelar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                   @Valid @RequestBody ReservaService.CancelamentoRequest request) {
        return service.cancelar(idCondominio, id, request.motivo());
    }
}
