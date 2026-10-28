package br.com.tekcondo.portal;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.announcement.AnnouncementService;
import br.com.tekcondo.document.controller.DocumentController;
import br.com.tekcondo.document.service.DocumentService;
import br.com.tekcondo.occurrence.OccurrenceService;
import br.com.tekcondo.reservation.ReservationService;
import br.com.tekcondo.visitor.VisitorService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/{c}/common-areas")
    public List<ReservationService.AreaResponse> commonAreas(@PathVariable UUID c) {
        return service.commonAreas(c);
    }

    @GetMapping("/{c}/common-areas/{areaId}/occupancy")
    public List<ReservationService.Slot> occupancy(@PathVariable UUID c, @PathVariable UUID areaId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.areaOccupancy(c, areaId, from, to);
    }

    @GetMapping("/{c}/reservations")
    public List<ReservationService.ReservationResponse> reservations(@PathVariable UUID c) {
        return service.myReservations(c);
    }

    @PostMapping("/{c}/reservations")
    public ResponseEntity<ReservationService.ReservationResponse> reserve(@PathVariable UUID c,
                                                                          @Valid @RequestBody ReservationService.ReservationRequest request) {
        return ResponseEntity.status(201).body(service.reserve(c, request));
    }

    @PostMapping("/{c}/reservations/{id}/cancel")
    public ReservationService.ReservationResponse cancelReservation(@PathVariable UUID c, @PathVariable UUID id,
                                                                    @Valid @RequestBody ReservationService.CancelRequest request) {
        return service.cancelReservation(c, id, request.reason());
    }

    @GetMapping("/{c}/visitors")
    public List<VisitorService.PassResponse> visitors(@PathVariable UUID c) {
        return service.visitorPasses(c);
    }

    @PostMapping("/{c}/visitors")
    public ResponseEntity<VisitorService.PassResponse> authorizeVisitor(@PathVariable UUID c,
                                                                        @Valid @RequestBody VisitorService.PassRequest request) {
        return ResponseEntity.status(201).body(service.authorizeVisitor(c, request));
    }

    @PostMapping("/{c}/visitors/{id}/cancel")
    public VisitorService.PassResponse cancelVisitor(@PathVariable UUID c, @PathVariable UUID id) {
        return service.cancelVisitor(c, id);
    }

    @GetMapping("/{c}/calendar")
    public List<PortalService.CalendarEvent> calendar(@PathVariable UUID c) {
        return service.calendar(c);
    }
}
