package br.com.tekcondo.condominio.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.condominio.dto.CondominioRequest;
import br.com.tekcondo.condominio.dto.CondominioResponse;
import br.com.tekcondo.condominio.dto.CondominioResumo;
import br.com.tekcondo.condominio.dto.InativacaoRequest;
import br.com.tekcondo.condominio.dto.OpcaoCondominio;
import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.condominio.service.CondominioService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominios")
@Tag(name = "Condomínios")
public class CondominioController {

    private static final Map<String, String> ORDENS = Map.of(
            "nome", "nome", "cidade", "endereco.cidade", "situacao", "situacao", "criadoEm", "criadoEm");

    private final CondominioService service;

    public CondominioController(CondominioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista condomínios visíveis ao usuário", description = "Busca por nome, razão social ou CNPJ. Ordenação: nome, cidade, situacao, criadoEm.")
    public PaginaResponse<CondominioResumo> listar(@RequestParam(required = false) String busca,
                                                   @RequestParam(required = false) SituacaoCondominio situacao,
                                                   @RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "20") int tamanho,
                                                   @RequestParam(required = false) String ordem) {
        return service.listar(busca, situacao, PaginaRequests.of(pagina, tamanho, ordem, ORDENS, Sort.by("nome").and(Sort.by("id"))));
    }

    @GetMapping("/acessiveis")
    @Operation(summary = "Condomínios acessíveis ao usuário, com permissões (seletor de condomínio)")
    public List<OpcaoCondominio> acessiveis() {
        return service.acessiveis();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um condomínio")
    public CondominioResponse obter(@PathVariable UUID id) {
        return service.obter(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra um condomínio")
    public ResponseEntity<CondominioResponse> cadastrar(@Valid @RequestBody CondominioRequest request) {
        CondominioResponse criado = service.cadastrar(request);
        return ResponseEntity.created(URI.create("/api/v1/condominios/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados cadastrais (exige a versão atual do registro)")
    public CondominioResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CondominioRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/inativar")
    @Operation(summary = "Inativa o condomínio com justificativa (histórico é preservado)")
    public CondominioResponse inativar(@PathVariable UUID id, @Valid @RequestBody InativacaoRequest request) {
        return service.inativar(id, request.motivo());
    }

    @PostMapping("/{id}/ativar")
    @Operation(summary = "Reativa o condomínio")
    public CondominioResponse ativar(@PathVariable UUID id) {
        return service.ativar(id);
    }
}
