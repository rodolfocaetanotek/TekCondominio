package br.com.tekcondo.identity.controller;

import br.com.tekcondo.identity.dto.PasswordRequests;
import br.com.tekcondo.identity.dto.PasswordTokenInfo;
import br.com.tekcondo.identity.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
@Tag(name = "Autenticação")
public class PasswordController {

    private final PasswordService service;

    public PasswordController(PasswordService service) {
        this.service = service;
    }

    @PostMapping("/forgot")
    @Operation(summary = "Solicita link de redefinição de senha",
            description = "Sempre responde 202, exista ou não o e-mail (não revela contas cadastradas).")
    public ResponseEntity<Void> forgot(@Valid @RequestBody PasswordRequests.Forgot request) {
        service.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/token")
    @Operation(summary = "Valida um link de convite/redefinição sem consumi-lo")
    public PasswordTokenInfo describe(@Valid @RequestBody PasswordRequests.TokenOnly request) {
        return service.describe(request.token());
    }

    @PostMapping("/reset")
    @Operation(summary = "Define a nova senha a partir do link (convite ou redefinição)")
    public ResponseEntity<Void> reset(@Valid @RequestBody PasswordRequests.Reset request) {
        service.complete(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change")
    @Operation(summary = "Troca a senha do usuário autenticado (encerra as demais sessões)")
    public ResponseEntity<Void> change(@Valid @RequestBody PasswordRequests.Change request,
                                       @CookieValue(name = AuthController.REFRESH_COOKIE, required = false) String refreshToken) {
        service.change(request.currentPassword(), request.newPassword(), refreshToken);
        return ResponseEntity.noContent().build();
    }
}
