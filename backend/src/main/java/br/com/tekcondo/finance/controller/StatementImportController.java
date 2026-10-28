package br.com.tekcondo.finance.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.finance.entity.StatementLine;
import br.com.tekcondo.finance.service.StatementImportService;
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
@RequestMapping("/api/v1/condominiums/{c}/finance")
@Tag(name = "Financeiro — extrato bancário (OFX) e conciliação")
public class StatementImportController {

    private final StatementImportService service;

    public StatementImportController(StatementImportService service) {
        this.service = service;
    }

    @PostMapping("/bank-accounts/{a}/statement-imports")
    @Operation(summary = "Importa extrato OFX e concilia automaticamente os pares inequívocos",
            description = "ignoreAccountCheck=true aceita extrato cujo número de conta difere do cadastro.")
    public ResponseEntity<StatementImportService.ImportResult> importOfx(@PathVariable UUID c, @PathVariable UUID a,
                                                                         @RequestPart("file") MultipartFile file,
                                                                         @RequestParam(defaultValue = "false") boolean ignoreAccountCheck) {
        try {
            return ResponseEntity.status(201).body(service.importOfx(c, a, file.getOriginalFilename(), file.getBytes(), ignoreAccountCheck));
        } catch (IOException e) {
            throw ApiException.requisicaoInvalida("UPLOAD_FAILED", "Não foi possível ler o arquivo enviado.");
        }
    }

    @GetMapping("/bank-accounts/{a}/statement-imports")
    public List<StatementImportService.ImportSummary> imports(@PathVariable UUID c, @PathVariable UUID a) {
        return service.imports(c, a);
    }

    @GetMapping("/bank-accounts/{a}/statement-lines")
    @Operation(summary = "Linhas do extrato importado (pendentes trazem sugestões de movimentações compatíveis)")
    public List<StatementImportService.LineView> lines(@PathVariable UUID c, @PathVariable UUID a,
                                                       @RequestParam(required = false) StatementLine.Status status) {
        return service.lines(c, a, status);
    }

    @PostMapping("/statement-lines/{id}/match")
    public StatementImportService.LineView match(@PathVariable UUID c, @PathVariable UUID id,
                                                 @Valid @RequestBody StatementImportService.MatchRequest request) {
        return service.match(c, id, request);
    }

    @PostMapping("/statement-lines/{id}/unmatch")
    public StatementImportService.LineView unmatch(@PathVariable UUID c, @PathVariable UUID id) {
        return service.unmatch(c, id);
    }

    @PostMapping("/statement-lines/{id}/ignore")
    public StatementImportService.LineView ignore(@PathVariable UUID c, @PathVariable UUID id,
                                                  @Valid @RequestBody StatementImportService.IgnoreRequest request) {
        return service.ignore(c, id, request);
    }

    @PostMapping("/statement-lines/{id}/register")
    @Operation(summary = "Lança e baixa a receita/despesa que só consta no extrato (tarifa, rendimento) e a concilia")
    public StatementImportService.LineView register(@PathVariable UUID c, @PathVariable UUID id,
                                                    @Valid @RequestBody StatementImportService.RegisterRequest request) {
        return service.register(c, id, request);
    }
}
