package br.com.tekcondo.reservation;

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
@RequestMapping("/api/v1/condominios/{c}")
@Tag(name = "Reservas de áreas comuns")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping("/common-areas")
    public List<ReservationService.AreaResponse> areas(@PathVariable UUID c) {
        return service.areas(c);
    }

    @PostMapping("/common-areas")
    public ResponseEntity<ReservationService.AreaResponse> createArea(@PathVariable UUID c, @Valid @RequestBody ReservationService.AreaRequest request) {
        return ResponseEntity.status(201).body(service.saveArea(c, null, request));
    }

    @PutMapping("/common-areas/{id}")
    public ReservationService.AreaResponse updateArea(@PathVariable UUID c, @PathVariable UUID id,
                                                      @Valid @RequestBody ReservationService.AreaRequest request) {
        return service.saveArea(c, id, request);
    }

    @GetMapping("/reservations")
    @Operation(summary = "Reservas do período (padrão: últimos 7 dias e próximos 60)")
    public List<ReservationService.ReservationResponse> list(@PathVariable UUID c, @RequestParam(required = false) UUID areaId,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(c, areaId, from, to);
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reserva lançada pela administração (confirmada direto; gera a taxa de uso, se houver)")
    public ResponseEntity<ReservationService.ReservationResponse> create(@PathVariable UUID c,
                                                                         @Valid @RequestBody ReservationService.ReservationRequest request) {
        return ResponseEntity.status(201).body(service.create(c, request));
    }

    @PostMapping("/reservations/{id}/approve")
    public ReservationService.ReservationResponse approve(@PathVariable UUID c, @PathVariable UUID id,
                                                          @Valid @RequestBody(required = false) ReservationService.DecisionRequest request) {
        return service.decide(c, id, true, request == null ? null : request.note());
    }

    @PostMapping("/reservations/{id}/reject")
    public ReservationService.ReservationResponse reject(@PathVariable UUID c, @PathVariable UUID id,
                                                         @Valid @RequestBody ReservationService.DecisionRequest request) {
        return service.decide(c, id, false, request.note());
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationService.ReservationResponse cancel(@PathVariable UUID c, @PathVariable UUID id,
                                                         @Valid @RequestBody ReservationService.CancelRequest request) {
        return service.cancel(c, id, request.reason());
    }
}
