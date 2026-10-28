package br.com.tekcondo.document.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.document.entity.Document;
import br.com.tekcondo.document.service.DocumentService;
import br.com.tekcondo.document.service.DocumentService.DocumentResponse;
import br.com.tekcondo.document.service.DocumentService.FileContent;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/condominios/{c}/documents")
@Tag(name = "Documentos e anexos")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<DocumentResponse> list(@PathVariable UUID c,
                                               @RequestParam(required = false) Document.Category category,
                                               @RequestParam(required = false) String entityType,
                                               @RequestParam(required = false) UUID entityId,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "false") boolean archived,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "50") int tamanho) {
        return service.list(c, category, entityType, entityId, search, archived,
                PaginaRequests.of(pagina, tamanho, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia documento ou anexo (até 10 MB; PDF, PNG, JPG, DOCX, XLSX, CSV, TXT)")
    public ResponseEntity<DocumentResponse> upload(@PathVariable UUID c, @RequestPart("file") MultipartFile file,
                                                   @RequestParam Document.Category category,
                                                   @RequestParam String title,
                                                   @RequestParam(required = false) String description,
                                                   @RequestParam(required = false) Document.Visibility visibility,
                                                   @RequestParam(required = false) String entityType,
                                                   @RequestParam(required = false) UUID entityId,
                                                   @RequestParam(required = false) UUID unitId) throws IOException {
        return ResponseEntity.status(201).body(service.upload(c, new DocumentService.Upload(category, title, description,
                visibility, entityType, entityId, unitId, file.getOriginalFilename(), file.getBytes())));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Baixa o arquivo (somente usuários autorizados; acesso auditado)")
    public ResponseEntity<byte[]> download(@PathVariable UUID c, @PathVariable UUID id) {
        return asAttachment(service.download(c, id));
    }

    @PostMapping("/{id}/archive")
    public DocumentResponse archive(@PathVariable UUID c, @PathVariable UUID id) {
        return service.archive(c, id);
    }

    public static ResponseEntity<byte[]> asAttachment(FileContent file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
