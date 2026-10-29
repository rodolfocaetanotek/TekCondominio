package br.com.tekcondo.comunicado;

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
@RequestMapping("/api/v1/condominios/{idCondominio}/comunicados")
@Tag(name = "Comunicados")
public class ComunicadoController {

    private final ComunicadoService service;

    public ComunicadoController(ComunicadoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ComunicadoService.ComunicadoResponse> listar(@PathVariable UUID idCondominio,
                                                             @RequestParam(defaultValue = "false") boolean arquivados) {
        return service.listar(idCondominio, arquivados);
    }

    @PostMapping
    @Operation(summary = "Publica comunicado (notifica o público-alvo; e-mail opcional)")
    public ResponseEntity<ComunicadoService.ComunicadoResponse> publicar(@PathVariable UUID idCondominio,
                                                                         @Valid @RequestBody ComunicadoService.ComunicadoRequest request) {
        return ResponseEntity.status(201).body(service.publicar(idCondominio, request));
    }

    @PostMapping("/{id}/arquivar")
    public ComunicadoService.ComunicadoResponse arquivar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.arquivar(idCondominio, id);
    }

    @PostMapping("/{id}/marcar-lido")
    public ResponseEntity<Void> marcarLido(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        service.marcarLido(idCondominio, id);
        return ResponseEntity.noContent().build();
    }
}
