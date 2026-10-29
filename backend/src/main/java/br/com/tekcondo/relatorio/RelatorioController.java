package br.com.tekcondo.relatorio;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominios/{idCondominio}/relatorios")
@Tag(name = "Relatórios")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @GetMapping("/{tipo}")
    @Operation(summary = "Gera o relatório (JSON para tela)",
            description = "tipo: receitas-despesas, balancete, fluxo-caixa, inadimplencia, contas-pagar, contas-receber, pagamentos, "
                    + "rateios, orcamento, movimentacao-bancaria, despesas-por-fornecedor, ocorrencias, contratos")
    public RelatorioTabela gerar(@PathVariable UUID idCondominio, @PathVariable String tipo,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                 @RequestParam(required = false) Integer ano,
                                 @RequestParam(required = false) UUID idContaBancaria) {
        return service.gerar(idCondominio, RelatorioService.Tipo.daChave(tipo), new RelatorioService.Parametros(de, ate, ano, idContaBancaria));
    }

    @GetMapping("/{tipo}/exportar")
    @Operation(summary = "Exporta o relatório em CSV, XLSX ou PDF (exportação auditada)")
    public ResponseEntity<byte[]> exportar(@PathVariable UUID idCondominio, @PathVariable String tipo,
                                           @RequestParam RelatorioExporter.Formato formato,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                           @RequestParam(required = false) Integer ano,
                                           @RequestParam(required = false) UUID idContaBancaria) {
        RelatorioService.ArquivoExportado arquivo = service.exportar(idCondominio, RelatorioService.Tipo.daChave(tipo),
                new RelatorioService.Parametros(de, ate, ano, idContaBancaria), formato);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(arquivo.tipoConteudo()))
                .body(arquivo.conteudo());
    }
}
