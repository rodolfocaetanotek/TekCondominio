package br.com.tekcondo.report;

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
@RequestMapping("/api/v1/condominiums/{c}/reports")
@Tag(name = "Relatórios")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/{kind}")
    @Operation(summary = "Gera o relatório (JSON para tela)",
            description = "kind: INCOME_EXPENSE, BALANCE_SHEET, CASH_FLOW, DELINQUENCY, PAYABLES, RECEIVABLES, PAYMENTS, ALLOCATIONS, "
                    + "BUDGET, BANK_MOVEMENT, EXPENSES_BY_SUPPLIER, OCCURRENCES, CONTRACTS")
    public ReportTable report(@PathVariable UUID c, @PathVariable ReportService.Kind kind,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) Integer year,
                              @RequestParam(required = false) UUID bankAccountId) {
        return service.build(c, kind, new ReportService.Params(from, to, year, bankAccountId));
    }

    @GetMapping("/{kind}/export")
    @Operation(summary = "Exporta o relatório em CSV, XLSX ou PDF (exportação auditada)")
    public ResponseEntity<byte[]> export(@PathVariable UUID c, @PathVariable ReportService.Kind kind,
                                         @RequestParam ReportExporter.Format format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) UUID bankAccountId) {
        ReportService.Exported file = service.export(c, kind, new ReportService.Params(from, to, year, bankAccountId), format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
