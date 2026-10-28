package br.com.tekcondo.estrutura.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.BlocoRequest;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.BlocoResponse;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.ResultadoImportacao;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.ResumoFracoes;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.UnidadeRequest;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.UnidadeResponse;
import br.com.tekcondo.estrutura.service.EstruturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/condominios/{idCondominio}")
@Tag(name = "Estrutura: blocos e unidades")
public class EstruturaController {

    private static final Map<String, String> ORDENS_UNIDADE = Map.of("identificacao", "identificacao",
            "fracaoIdeal", "fracaoIdeal", "tipo", "tipo");
    private static final long MAX_BYTES_IMPORTACAO = 2L * 1024 * 1024;

    private final EstruturaService service;

    public EstruturaController(EstruturaService service) {
        this.service = service;
    }

    @GetMapping("/blocos")
    @Operation(summary = "Lista blocos/torres")
    public List<BlocoResponse> blocos(@PathVariable UUID idCondominio) {
        return service.blocos(idCondominio);
    }

    @PostMapping("/blocos")
    @Operation(summary = "Cadastra bloco/torre")
    public ResponseEntity<BlocoResponse> cadastrarBloco(@PathVariable UUID idCondominio,
                                                        @Valid @RequestBody BlocoRequest request) {
        return ResponseEntity.status(201).body(service.cadastrarBloco(idCondominio, request));
    }

    @PutMapping("/blocos/{id}")
    public BlocoResponse atualizarBloco(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                        @Valid @RequestBody BlocoRequest request) {
        return service.atualizarBloco(idCondominio, id, request);
    }

    @PostMapping("/blocos/{id}/ativar")
    public BlocoResponse ativarBloco(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.setBlocoAtivo(idCondominio, id, true);
    }

    @PostMapping("/blocos/{id}/desativar")
    public BlocoResponse desativarBloco(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.setBlocoAtivo(idCondominio, id, false);
    }

    @GetMapping("/unidades")
    @Operation(summary = "Lista unidades com proprietário e responsável financeiro vigentes")
    public PaginaResponse<UnidadeResponse> unidades(@PathVariable UUID idCondominio,
                                                    @RequestParam(required = false) String busca,
                                                    @RequestParam(required = false) UUID idBloco,
                                                    @RequestParam(required = false) Boolean ativo,
                                                    @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "50") int tamanho,
                                                    @RequestParam(required = false) String ordem) {
        return service.unidades(idCondominio, busca, idBloco, ativo,
                PaginaRequests.of(pagina, tamanho, ordem, ORDENS_UNIDADE, Sort.by("identificacao").and(Sort.by("id"))));
    }

    @GetMapping("/unidades/resumo-fracoes")
    @Operation(summary = "Soma das frações ideais das unidades ativas (deve totalizar 100%)")
    public ResumoFracoes resumoFracoes(@PathVariable UUID idCondominio) {
        return service.resumoFracoes(idCondominio);
    }

    @GetMapping("/unidades/{id}")
    public UnidadeResponse unidade(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.unidade(idCondominio, id);
    }

    @PostMapping("/unidades")
    @Operation(summary = "Cadastra unidade")
    public ResponseEntity<UnidadeResponse> cadastrarUnidade(@PathVariable UUID idCondominio,
                                                            @Valid @RequestBody UnidadeRequest request) {
        return ResponseEntity.status(201).body(service.cadastrarUnidade(idCondominio, request));
    }

    @PutMapping("/unidades/{id}")
    public UnidadeResponse atualizarUnidade(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                            @Valid @RequestBody UnidadeRequest request) {
        return service.atualizarUnidade(idCondominio, id, request);
    }

    @PostMapping("/unidades/{id}/ativar")
    public UnidadeResponse ativarUnidade(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.setUnidadeAtiva(idCondominio, id, true);
    }

    @PostMapping("/unidades/{id}/desativar")
    public UnidadeResponse desativarUnidade(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.setUnidadeAtiva(idCondominio, id, false);
    }

    @PostMapping(value = "/unidades/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa unidades e proprietários de planilha CSV",
            description = "Colunas: bloco; unidade; tipo; fracao; area; proprietario; documento; email; telefone. "
                    + "Use simulacao=true para validar sem gravar. Com qualquer erro, nada é gravado.")
    public ResultadoImportacao importarUnidades(@PathVariable UUID idCondominio,
                                                @RequestPart("arquivo") MultipartFile arquivo,
                                                @RequestParam(defaultValue = "false") boolean simulacao) throws IOException {
        if (arquivo.isEmpty() || arquivo.getSize() > MAX_BYTES_IMPORTACAO) {
            throw ApiException.requisicaoInvalida("INVALID_FILE", "Envie um arquivo CSV de até 2 MB.");
        }
        return service.importarUnidades(idCondominio, arquivo.getBytes(), simulacao);
    }
}
