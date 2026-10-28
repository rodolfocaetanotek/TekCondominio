package br.com.tekcondo.portal;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.announcement.AnnouncementService;
import br.com.tekcondo.document.controller.DocumentController;
import br.com.tekcondo.document.service.DocumentService;
import br.com.tekcondo.occurrence.OccurrenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal")
@Tag(name = "Portal do morador")
public class PortalController {

    private final PortalService service;

    public PortalController(PortalService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public PortalService.Me me() {
        return service.me();
    }

    @GetMapping("/{c}/charges")
    public List<PortalService.UnitCharges> charges(@PathVariable UUID c) {
        return service.charges(c);
    }

    @GetMapping("/{c}/announcements")
    public List<AnnouncementService.Response> announcements(@PathVariable UUID c) {
        return service.announcements(c);
    }

    @PostMapping("/{c}/announcements/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID c, @PathVariable UUID id) {
        service.markAnnouncementRead(c, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{c}/documents")
    public List<DocumentService.DocumentResponse> documents(@PathVariable UUID c) {
        return service.documents(c);
    }

    @GetMapping("/{c}/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID c, @PathVariable UUID id) {
        return DocumentController.asAttachment(service.download(c, id));
    }

    @GetMapping("/{c}/occurrences")
    public List<OccurrenceService.Summary> occurrences(@PathVariable UUID c) {
        return service.occurrences(c);
    }

    @GetMapping("/{c}/occurrences/{id}")
    public OccurrenceService.Detail occurrence(@PathVariable UUID c, @PathVariable UUID id) {
        return service.occurrence(c, id);
    }

    @PostMapping("/{c}/occurrences")
    public ResponseEntity<OccurrenceService.Detail> open(@PathVariable UUID c, @Valid @RequestBody OccurrenceService.CreateRequest request) {
        return ResponseEntity.status(201).body(service.openOccurrence(c, request));
    }

    @PostMapping("/{c}/occurrences/{id}/comments")
    public OccurrenceService.Detail comment(@PathVariable UUID c, @PathVariable UUID id,
                                            @Valid @RequestBody OccurrenceService.CommentRequest request) {
        return service.commentOccurrence(c, id, request.message());
    }

    @GetMapping("/{c}/assemblies")
    public List<PortalService.AssemblyInfo> assemblies(@PathVariable UUID c) {
        return service.assemblies(c);
    }

    @GetMapping("/{c}/calendar")
    public List<PortalService.CalendarEvent> calendar(@PathVariable UUID c) {
        return service.calendar(c);
    }
}
