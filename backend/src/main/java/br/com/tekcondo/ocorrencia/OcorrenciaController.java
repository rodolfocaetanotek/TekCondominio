package br.com.tekcondo.ocorrencia;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
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
@RequestMapping("/api/v1/condominios/{idCondominio}/ocorrencias")
@Tag(name = "Ocorrências e manutenção")
public class OcorrenciaController {

    private static final Map<String, String> ORDENS = Map.of("numero", "numero", "criadoEm", "criadoEm",
            "prioridade", "prioridade", "prazo", "prazo");

    private final OcorrenciaService service;

    public OcorrenciaController(OcorrenciaService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<OcorrenciaService.Resumo> listar(@PathVariable UUID idCondominio,
                                                           @RequestParam(required = false) List<Ocorrencia.Situacao> situacao,
                                                           @RequestParam(required = false) Ocorrencia.Categoria categoria,
                                                           @RequestParam(required = false) Ocorrencia.Prioridade prioridade,
                                                           @RequestParam(required = false) String busca,
                                                           @RequestParam(defaultValue = "0") int pagina,
                                                           @RequestParam(defaultValue = "30") int tamanho,
                                                           @RequestParam(required = false) String ordem) {
        return service.listar(idCondominio, situacao, categoria, prioridade, busca,
                PaginaRequests.of(pagina, tamanho, ordem, ORDENS, Sort.by(Sort.Direction.DESC, "criadoEm")));
    }

    @GetMapping("/estatisticas")
    public OcorrenciaService.Estatisticas estatisticas(@PathVariable UUID idCondominio) {
        return service.estatisticas(idCondominio);
    }

    @GetMapping("/{id}")
    public OcorrenciaService.Detalhe buscar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.buscar(idCondominio, id);
    }

    @PostMapping
    public ResponseEntity<OcorrenciaService.Detalhe> cadastrar(@PathVariable UUID idCondominio,
                                                               @Valid @RequestBody OcorrenciaService.CadastroRequest request) {
        return ResponseEntity.status(201).body(service.cadastrar(idCondominio, request));
    }

    @PutMapping("/{id}")
    public OcorrenciaService.Detalhe atualizar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                               @Valid @RequestBody OcorrenciaService.TriagemRequest request) {
        return service.atualizar(idCondominio, id, request);
    }

    @PostMapping("/{id}/situacao")
    public OcorrenciaService.Detalhe alterarSituacao(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                     @Valid @RequestBody OcorrenciaService.SituacaoRequest request) {
        return service.alterarSituacao(idCondominio, id, request);
    }

    @PostMapping("/{id}/interacoes")
    public OcorrenciaService.Detalhe comentar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                              @Valid @RequestBody OcorrenciaService.ComentarioRequest request) {
        return service.comentar(idCondominio, id, request);
    }
}
