package br.com.tekcondo.announcement;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominios/{c}/announcements")
@Tag(name = "Comunicados")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnnouncementService.Response> list(@PathVariable UUID c, @RequestParam(defaultValue = "false") boolean archived) {
        return service.list(c, archived);
    }

    @PostMapping
    @Operation(summary = "Publica comunicado (notifica o público-alvo; e-mail opcional)")
    public ResponseEntity<AnnouncementService.Response> publish(@PathVariable UUID c, @Valid @RequestBody AnnouncementService.Request request) {
        return ResponseEntity.status(201).body(service.publish(c, request));
    }

    @PostMapping("/{id}/archive")
    public AnnouncementService.Response archive(@PathVariable UUID c, @PathVariable UUID id) {
        return service.archive(c, id);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID c, @PathVariable UUID id) {
        service.markRead(c, id);
        return ResponseEntity.noContent().build();
    }
}
