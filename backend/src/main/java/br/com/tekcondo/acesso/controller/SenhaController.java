package br.com.tekcondo.acesso.controller;

import br.com.tekcondo.acesso.dto.SenhaRequests;
import br.com.tekcondo.acesso.dto.TokenSenhaInfo;
import br.com.tekcondo.acesso.service.SenhaService;
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
@RequestMapping("/api/v1/auth/senha")
@Tag(name = "Autenticação")
public class SenhaController {

    private final SenhaService service;

    public SenhaController(SenhaService service) {
        this.service = service;
    }

    @PostMapping("/esqueci")
    @Operation(summary = "Solicita link de redefinição de senha",
            description = "Sempre responde 202, exista ou não o e-mail (não revela contas cadastradas).")
    public ResponseEntity<Void> esqueci(@Valid @RequestBody SenhaRequests.Esqueci request) {
        service.solicitarRedefinicao(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/token")
    @Operation(summary = "Valida um link de convite/redefinição sem consumi-lo")
    public TokenSenhaInfo descrever(@Valid @RequestBody SenhaRequests.SomenteToken request) {
        return service.descrever(request.token());
    }

    @PostMapping("/redefinir")
    @Operation(summary = "Define a nova senha a partir do link (convite ou redefinição)")
    public ResponseEntity<Void> redefinir(@Valid @RequestBody SenhaRequests.Redefinicao request) {
        service.concluir(request.token(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/alterar")
    @Operation(summary = "Troca a senha do usuário autenticado (encerra as demais sessões)")
    public ResponseEntity<Void> alterar(@Valid @RequestBody SenhaRequests.Alteracao request,
                                       @CookieValue(name = AuthController.REFRESH_COOKIE, required = false) String refreshToken) {
        service.alterar(request.senhaAtual(), request.novaSenha(), refreshToken);
        return ResponseEntity.noContent().build();
    }
}
