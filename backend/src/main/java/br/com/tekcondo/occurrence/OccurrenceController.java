package br.com.tekcondo.occurrence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/condominios/{c}/occurrences")
@Tag(name = "Ocorrências e manutenção")
public class OccurrenceController {

    private static final Map<String, String> SORTS = Map.of("number", "number", "createdAt", "criadoEm",
            "priority", "priority", "dueDate", "dueDate");

    private final OccurrenceService service;

    public OccurrenceController(OccurrenceService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<OccurrenceService.Summary> list(@PathVariable UUID c,
                                                        @RequestParam(required = false) List<Occurrence.Status> status,
                                                        @RequestParam(required = false) Occurrence.Category category,
                                                        @RequestParam(required = false) Occurrence.Priority priority,
                                                        @RequestParam(required = false) String search,
                                                        @RequestParam(defaultValue = "0") int pagina,
                                                        @RequestParam(defaultValue = "30") int tamanho,
                                                        @RequestParam(required = false) String ordem) {
        return service.list(c, status, category, priority, search,
                PaginaRequests.of(pagina, tamanho, ordem, SORTS, Sort.by(Sort.Direction.DESC, "criadoEm")));
    }

    @GetMapping("/stats")
    public OccurrenceService.Stats stats(@PathVariable UUID c) {
        return service.stats(c);
    }

    @GetMapping("/{id}")
    public OccurrenceService.Detail get(@PathVariable UUID c, @PathVariable UUID id) {
        return service.get(c, id);
    }

    @PostMapping
    public ResponseEntity<OccurrenceService.Detail> create(@PathVariable UUID c, @Valid @RequestBody OccurrenceService.CreateRequest request) {
        return ResponseEntity.status(201).body(service.create(c, request));
    }

    @PutMapping("/{id}")
    public OccurrenceService.Detail update(@PathVariable UUID c, @PathVariable UUID id,
                                           @Valid @RequestBody OccurrenceService.UpdateRequest request) {
        return service.update(c, id, request);
    }

    @PostMapping("/{id}/status")
    public OccurrenceService.Detail status(@PathVariable UUID c, @PathVariable UUID id,
                                           @Valid @RequestBody OccurrenceService.StatusRequest request) {
        return service.changeStatus(c, id, request);
    }

    @PostMapping("/{id}/comments")
    public OccurrenceService.Detail comment(@PathVariable UUID c, @PathVariable UUID id,
                                            @Valid @RequestBody OccurrenceService.CommentRequest request) {
        return service.comment(c, id, request);
    }
}
