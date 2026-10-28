package br.com.tekcondo.acesso.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.acesso.dto.UsuarioDtos;
import br.com.tekcondo.acesso.service.UsuarioService;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Usuários e papéis")
public class UsuarioController {

    private static final Map<String, String> ORDENS = Map.of("nome", "nome", "email", "email", "ultimoLoginEm", "ultimoLoginEm");

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Lista usuários da administradora")
    public PaginaResponse<UsuarioDtos.Resumo> listar(@RequestParam(required = false) String busca,
                                               @RequestParam(required = false) Boolean ativo,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamanho,
                                               @RequestParam(required = false) String ordem) {
        return service.listar(busca, ativo, PaginaRequests.of(pagina, tamanho, ordem, ORDENS, Sort.by("nome").and(Sort.by("id"))));
    }

    @GetMapping("/usuarios/{id}")
    @Operation(summary = "Consulta usuário com seus papéis")
    public UsuarioDtos.Detalhe obter(@PathVariable UUID id) {
        return service.obter(id);
    }

    @PostMapping("/usuarios")
    @Operation(summary = "Cadastra usuário e envia convite por e-mail para definição da senha")
    public ResponseEntity<UsuarioDtos.Detalhe> cadastrar(@Valid @RequestBody UsuarioDtos.CadastroRequest request) {
        UsuarioDtos.Detalhe criado = service.cadastrar(request);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + criado.id())).body(criado);
    }

    @PutMapping("/usuarios/{id}")
    @Operation(summary = "Atualiza o nome do usuário")
    public UsuarioDtos.Detalhe atualizar(@PathVariable UUID id, @Valid @RequestBody UsuarioDtos.AtualizacaoRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/usuarios/{id}/desativar")
    @Operation(summary = "Desativa o usuário e encerra suas sessões")
    public UsuarioDtos.Detalhe desativar(@PathVariable UUID id) {
        return service.desativar(id);
    }

    @PostMapping("/usuarios/{id}/ativar")
    @Operation(summary = "Reativa o usuário")
    public UsuarioDtos.Detalhe ativar(@PathVariable UUID id) {
        return service.ativar(id);
    }

    @PostMapping("/usuarios/{id}/atribuicoes")
    @Operation(summary = "Atribui papel (para toda a administradora ou para um condomínio)")
    public UsuarioDtos.Detalhe adicionarAtribuicao(@PathVariable UUID id, @Valid @RequestBody UsuarioDtos.AtribuicaoRequest request) {
        return service.adicionarAtribuicao(id, request);
    }

    @DeleteMapping("/usuarios/{id}/atribuicoes/{idAtribuicao}")
    @Operation(summary = "Remove uma atribuição de papel")
    public UsuarioDtos.Detalhe removerAtribuicao(@PathVariable UUID id, @PathVariable UUID idAtribuicao) {
        return service.removerAtribuicao(id, idAtribuicao);
    }

    @PostMapping("/usuarios/{id}/convite")
    @Operation(summary = "Reenvia o convite (somente para quem ainda não acessou)")
    public ResponseEntity<Void> reenviarConvite(@PathVariable UUID id) {
        service.reenviarConvite(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/papeis")
    @Operation(summary = "Papéis disponíveis, com escopo e permissões")
    public List<UsuarioDtos.PapelResponse> papeis() {
        return service.papeis();
    }
}
