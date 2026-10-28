package br.com.tekcondo.pessoa.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.pessoa.dto.PessoaDtos.EncerramentoVinculoRequest;
import br.com.tekcondo.pessoa.dto.PessoaDtos.PessoaDetalhe;
import br.com.tekcondo.pessoa.dto.PessoaDtos.PessoaRequest;
import br.com.tekcondo.pessoa.dto.PessoaDtos.PessoaResumo;
import br.com.tekcondo.pessoa.dto.PessoaDtos.VinculoRequest;
import br.com.tekcondo.pessoa.dto.PessoaDtos.VinculoResponse;
import br.com.tekcondo.pessoa.service.PessoaService;
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
@RequestMapping("/api/v1")
@Tag(name = "Pessoas e vínculos")
public class PessoaController {

    private static final Map<String, String> ORDENS = Map.of("nome", "nome", "criadoEm", "criadoEm");

    private final PessoaService service;

    public PessoaController(PessoaService service) {
        this.service = service;
    }

    @GetMapping("/pessoas")
    @Operation(summary = "Lista pessoas (busca por nome, e-mail ou CPF/CNPJ)")
    public PaginaResponse<PessoaResumo> listar(@RequestParam(required = false) String busca,
                                               @RequestParam(required = false) Boolean ativo,
                                               @RequestParam(required = false) UUID idCondominio,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamanho,
                                               @RequestParam(required = false) String ordem) {
        return service.listar(busca, ativo, idCondominio,
                PaginaRequests.of(pagina, tamanho, ordem, ORDENS, Sort.by("nome").and(Sort.by("id"))));
    }

    @GetMapping("/pessoas/{id}")
    public PessoaDetalhe obter(@PathVariable UUID id) {
        return service.obter(id);
    }

    @PostMapping("/pessoas")
    public ResponseEntity<PessoaDetalhe> cadastrar(@Valid @RequestBody PessoaRequest request) {
        return ResponseEntity.status(201).body(service.cadastrar(request));
    }

    @PutMapping("/pessoas/{id}")
    public PessoaDetalhe atualizar(@PathVariable UUID id, @Valid @RequestBody PessoaRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/pessoas/{id}/ativar")
    public PessoaDetalhe ativar(@PathVariable UUID id) {
        return service.setAtivo(id, true);
    }

    @PostMapping("/pessoas/{id}/desativar")
    public PessoaDetalhe desativar(@PathVariable UUID id) {
        return service.setAtivo(id, false);
    }

    @PostMapping("/pessoas/{id}/acesso-portal")
    @Operation(summary = "Concede acesso ao portal do morador (convite por e-mail)")
    public PessoaDetalhe acessoPortal(@PathVariable UUID id) {
        return service.concederAcessoPortal(id);
    }

    @GetMapping("/condominios/{idCondominio}/unidades/{idUnidade}/vinculos")
    @Operation(summary = "Histórico de vínculos da unidade")
    public List<VinculoResponse> vinculosDaUnidade(@PathVariable UUID idCondominio, @PathVariable UUID idUnidade) {
        return service.vinculosDaUnidade(idCondominio, idUnidade);
    }

    @PostMapping("/condominios/{idCondominio}/vinculos")
    @Operation(summary = "Vincula pessoa a unidade (proprietário, inquilino ou morador)")
    public ResponseEntity<VinculoResponse> adicionarVinculo(@PathVariable UUID idCondominio,
                                                            @Valid @RequestBody VinculoRequest request) {
        return ResponseEntity.status(201).body(service.adicionarVinculo(idCondominio, request));
    }

    @PostMapping("/condominios/{idCondominio}/vinculos/{id}/encerrar")
    @Operation(summary = "Encerra o vínculo (preserva o histórico)")
    public VinculoResponse encerrarVinculo(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                           @Valid @RequestBody EncerramentoVinculoRequest request) {
        return service.encerrarVinculo(idCondominio, id, request);
    }

    @PostMapping("/condominios/{idCondominio}/vinculos/{id}/responsavel-financeiro")
    @Operation(summary = "Define este vínculo como responsável financeiro da unidade")
    public VinculoResponse definirResponsavelFinanceiro(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.definirResponsavelFinanceiro(idCondominio, id);
    }
}
