package br.com.tekcondo.administradora.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.administradora.dto.ConviteAdministradorRequest;
import br.com.tekcondo.administradora.dto.AdministradoraRequest;
import br.com.tekcondo.administradora.dto.UsuarioAdministradora;
import br.com.tekcondo.administradora.dto.AdministradoraResponse;
import br.com.tekcondo.administradora.service.AdministradoraService;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/administradoras")
@Tag(name = "Organizações (administrador da plataforma)")
public class AdministradoraController {

    private static final Map<String, String> ORDENS = Map.of("nome", "nome", "criadoEm", "criadoEm");

    private final AdministradoraService service;

    public AdministradoraController(AdministradoraService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista administradoras")
    public PaginaResponse<AdministradoraResponse> listar(@RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "20") int tamanho,
                                                   @RequestParam(required = false) String ordem) {
        return service.listar(PaginaRequests.of(pagina, tamanho, ordem, ORDENS, Sort.by("nome")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma administradora")
    public AdministradoraResponse obter(@PathVariable UUID id) {
        return service.obter(id);
    }

    @GetMapping("/{id}/usuarios")
    @Operation(summary = "Usuários da administradora")
    public List<UsuarioAdministradora> usuarios(@PathVariable UUID id) {
        return service.usuarios(id);
    }

    @PostMapping("/{id}/administradores")
    @Operation(summary = "Convida um administrador para a administradora (definição de senha por e-mail)")
    public ResponseEntity<UsuarioAdministradora> convidarAdministrador(@PathVariable UUID id,
                                                        @Valid @RequestBody ConviteAdministradorRequest request) {
        return ResponseEntity.status(201).body(service.convidarAdministrador(id, request.nome().trim(), request.email().trim()));
    }

    @PostMapping("/{id}/ativar")
    @Operation(summary = "Reativa a administradora")
    public AdministradoraResponse ativar(@PathVariable UUID id) {
        return service.setAtivo(id, true);
    }

    @PostMapping("/{id}/desativar")
    @Operation(summary = "Inativa a administradora (bloqueia o acesso de todos os seus usuários)")
    public AdministradoraResponse desativar(@PathVariable UUID id) {
        return service.setAtivo(id, false);
    }

    @PostMapping
    @Operation(summary = "Cadastra administradora e seu primeiro administrador")
    public ResponseEntity<AdministradoraResponse> cadastrar(@Valid @RequestBody AdministradoraRequest request) {
        AdministradoraResponse criado = service.cadastrar(request);
        return ResponseEntity.created(URI.create("/api/v1/administradoras/" + criado.id())).body(criado);
    }
}
