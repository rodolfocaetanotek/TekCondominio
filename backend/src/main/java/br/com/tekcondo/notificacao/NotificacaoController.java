package br.com.tekcondo.notificacao;

import java.util.UUID;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificacoes")
@Tag(name = "Notificações")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    public NotificacaoService.CaixaEntrada caixaEntrada() {
        return service.caixaEntrada();
    }

    @PostMapping("/{id}/marcar-lida")
    public ResponseEntity<Void> marcarLida(@PathVariable UUID id) {
        service.marcarLida(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/marcar-todas-lidas")
    public ResponseEntity<Void> marcarTodasLidas() {
        service.marcarTodasLidas();
        return ResponseEntity.noContent().build();
    }
}
