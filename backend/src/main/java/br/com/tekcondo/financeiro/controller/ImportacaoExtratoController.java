package br.com.tekcondo.financeiro.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.financeiro.entity.LinhaExtrato;
import br.com.tekcondo.financeiro.service.ImportacaoExtratoService;
import br.com.tekcondo.comum.exception.ApiException;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/condominios/{idCondominio}/financeiro")
@Tag(name = "Financeiro — extrato bancário (OFX) e conciliação")
public class ImportacaoExtratoController {

    private final ImportacaoExtratoService service;

    public ImportacaoExtratoController(ImportacaoExtratoService service) {
        this.service = service;
    }

    @PostMapping("/contas-bancarias/{idConta}/extratos")
    @Operation(summary = "Importa extrato OFX e concilia automaticamente os pares inequívocos",
            description = "ignorarConferenciaConta=true aceita extrato cujo número de conta difere do cadastro.")
    public ResponseEntity<ImportacaoExtratoService.ResultadoImportacao> importarOfx(@PathVariable UUID idCondominio,
                                                                                    @PathVariable UUID idConta,
                                                                                    @RequestPart("arquivo") MultipartFile arquivo,
                                                                                    @RequestParam(defaultValue = "false") boolean ignorarConferenciaConta) {
        try {
            return ResponseEntity.status(201).body(service.importarOfx(idCondominio, idConta, arquivo.getOriginalFilename(),
                    arquivo.getBytes(), ignorarConferenciaConta));
        } catch (IOException e) {
            throw ApiException.requisicaoInvalida("UPLOAD_FAILED", "Não foi possível ler o arquivo enviado.");
        }
    }

    @GetMapping("/contas-bancarias/{idConta}/extratos")
    public List<ImportacaoExtratoService.ResumoImportacao> importacoes(@PathVariable UUID idCondominio, @PathVariable UUID idConta) {
        return service.importacoes(idCondominio, idConta);
    }

    @GetMapping("/contas-bancarias/{idConta}/linhas-extrato")
    @Operation(summary = "Linhas do extrato importado (pendentes trazem sugestões de movimentações compatíveis)")
    public List<ImportacaoExtratoService.LinhaExtratoResponse> linhas(@PathVariable UUID idCondominio, @PathVariable UUID idConta,
                                                                      @RequestParam(required = false) LinhaExtrato.Situacao situacao) {
        return service.linhas(idCondominio, idConta, situacao);
    }

    @PostMapping("/linhas-extrato/{id}/conciliar")
    public ImportacaoExtratoService.LinhaExtratoResponse conciliar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                                   @Valid @RequestBody ImportacaoExtratoService.ConciliarRequest request) {
        return service.conciliar(idCondominio, id, request);
    }

    @PostMapping("/linhas-extrato/{id}/desfazer-conciliacao")
    public ImportacaoExtratoService.LinhaExtratoResponse desfazerConciliacao(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.desfazerConciliacao(idCondominio, id);
    }

    @PostMapping("/linhas-extrato/{id}/ignorar")
    public ImportacaoExtratoService.LinhaExtratoResponse ignorar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                                 @Valid @RequestBody ImportacaoExtratoService.IgnorarRequest request) {
        return service.ignorar(idCondominio, id, request);
    }

    @PostMapping("/linhas-extrato/{id}/lancar")
    @Operation(summary = "Lança e baixa a receita/despesa que só consta no extrato (tarifa, rendimento) e a concilia")
    public ImportacaoExtratoService.LinhaExtratoResponse lancar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                                @Valid @RequestBody ImportacaoExtratoService.LancarRequest request) {
        return service.lancar(idCondominio, id, request);
    }
}
