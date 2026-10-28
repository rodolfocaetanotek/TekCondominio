package br.com.tekcondo.assembly;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.finance.dto.FinanceDtos.ReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominiums/{c}/assemblies")
@Tag(name = "Assembleias e atas")
public class AssemblyController {

    private final AssemblyService service;

    public AssemblyController(AssemblyService service) {
        this.service = service;
    }

    @GetMapping
    public List<AssemblyService.Summary> list(@PathVariable UUID c) {
        return service.list(c);
    }

    @GetMapping("/{id}")
    public AssemblyService.Detail get(@PathVariable UUID c, @PathVariable UUID id) {
        return service.get(c, id);
    }

    @PostMapping
    public ResponseEntity<AssemblyService.Detail> create(@PathVariable UUID c, @Valid @RequestBody AssemblyService.Request request) {
        return ResponseEntity.status(201).body(service.create(c, request));
    }

    @PutMapping("/{id}")
    public AssemblyService.Detail update(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody AssemblyService.Request request) {
        return service.update(c, id, request);
    }

    @PostMapping("/{id}/call")
    @Operation(summary = "Convoca: publica comunicado com a pauta para todos os condôminos")
    public AssemblyService.Detail call(@PathVariable UUID c, @PathVariable UUID id) {
        return service.call(c, id);
    }

    @PostMapping("/{id}/start")
    public AssemblyService.Detail start(@PathVariable UUID c, @PathVariable UUID id) {
        return service.start(c, id);
    }

    @PostMapping("/{id}/close")
    public AssemblyService.Detail close(@PathVariable UUID c, @PathVariable UUID id) {
        return service.close(c, id);
    }

    @PostMapping("/{id}/cancel")
    public AssemblyService.Detail cancel(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return service.cancel(c, id, request.reason());
    }

    @PostMapping("/{id}/attendance")
    @Operation(summary = "Registra presença da unidade (pessoalmente ou por procuração)")
    public AssemblyService.Detail attend(@PathVariable UUID c, @PathVariable UUID id,
                                         @Valid @RequestBody AssemblyService.AttendanceRequest request) {
        return service.registerAttendance(c, id, request);
    }

    @DeleteMapping("/{id}/attendance/{attendanceId}")
    public AssemblyService.Detail removeAttendance(@PathVariable UUID c, @PathVariable UUID id, @PathVariable UUID attendanceId) {
        return service.removeAttendance(c, id, attendanceId);
    }

    @PostMapping("/{id}/votes")
    @Operation(summary = "Registra o voto de uma unidade presente em um item deliberativo")
    public AssemblyService.Detail vote(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody AssemblyService.VoteRequest request) {
        return service.vote(c, id, request);
    }

    @PutMapping("/{id}/agenda/{itemId}/deliberation")
    public AssemblyService.Detail deliberate(@PathVariable UUID c, @PathVariable UUID id, @PathVariable UUID itemId,
                                             @Valid @RequestBody AssemblyService.DeliberationRequest request) {
        return service.deliberate(c, id, itemId, request);
    }

    @PutMapping("/{id}/minutes")
    public AssemblyService.Detail minutes(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody AssemblyService.MinutesRequest request) {
        return service.writeMinutes(c, id, request.minutes());
    }

    @GetMapping("/{id}/minutes-draft")
    @Operation(summary = "Rascunho da ata gerado a partir dos dados registrados")
    public Map<String, String> minutesDraft(@PathVariable UUID c, @PathVariable UUID id) {
        return Map.of("minutes", service.minutesDraft(c, id));
    }
}
