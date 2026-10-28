package br.com.tekcondo.billing.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.billing.dto.BillingDtos.AgreementRequest;
import br.com.tekcondo.billing.dto.BillingDtos.AgreementResponse;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationPreview;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationRequest;
import br.com.tekcondo.billing.dto.BillingDtos.AllocationResponse;
import br.com.tekcondo.billing.dto.BillingDtos.DebtImportResult;
import br.com.tekcondo.billing.dto.BillingDtos.DelinquencyReport;
import br.com.tekcondo.billing.dto.BillingDtos.NoteRequest;
import br.com.tekcondo.billing.dto.BillingDtos.NoteResponse;
import br.com.tekcondo.billing.dto.BillingDtos.UnitDebt;
import br.com.tekcondo.billing.service.AllocationService;
import br.com.tekcondo.billing.service.CollectionService;
import br.com.tekcondo.billing.service.DebtImportService;
import br.com.tekcondo.finance.dto.FinanceDtos.ReasonRequest;
import br.com.tekcondo.shared.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/condominiums/{c}/billing")
@Tag(name = "Cobrança e inadimplência")
public class BillingController {

    private static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024;

    private final AllocationService allocations;
    private final CollectionService collection;
    private final DebtImportService debtImport;

    public BillingController(AllocationService allocations, CollectionService collection, DebtImportService debtImport) {
        this.allocations = allocations;
        this.collection = collection;
        this.debtImport = debtImport;
    }

    @PostMapping(value = "/debts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa débitos em aberto de outro sistema (CSV)",
            description = "Colunas: bloco; unidade; competencia (MM/AAAA); vencimento (DD/MM/AAAA); valor; descricao; "
                    + "documento; conta (padrão 1.01). Use dryRun=true para validar sem gravar. Com qualquer erro, nada é gravado.")
    public DebtImportResult importDebts(@PathVariable UUID c, @RequestPart("file") MultipartFile file,
                                        @RequestParam(defaultValue = "false") boolean dryRun) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_IMPORT_BYTES) {
            throw ApiException.badRequest("INVALID_FILE", "Envie um arquivo CSV de até 2 MB.");
        }
        return debtImport.importDebts(c, file.getBytes(), dryRun);
    }

    @GetMapping("/allocations")
    @Operation(summary = "Rateios / cobranças geradas por competência")
    public List<AllocationResponse> allocations(@PathVariable UUID c) {
        return allocations.list(c);
    }

    @PostMapping("/allocations/preview")
    @Operation(summary = "Simula o rateio (valor por unidade) sem gravar")
    public AllocationPreview preview(@PathVariable UUID c, @Valid @RequestBody AllocationRequest request) {
        return allocations.preview(c, request);
    }

    @PostMapping("/allocations")
    @Operation(summary = "Gera as cobranças da competência (uma por unidade ativa)")
    public ResponseEntity<AllocationResponse> generate(@PathVariable UUID c, @Valid @RequestBody AllocationRequest request) {
        return ResponseEntity.status(201).body(allocations.generate(c, request));
    }

    @PostMapping("/allocations/{id}/cancel")
    public AllocationResponse cancel(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return allocations.cancel(c, id, request.reason());
    }

    @GetMapping("/delinquency")
    @Operation(summary = "Unidades inadimplentes com valores atualizados (multa e juros) até a data")
    public DelinquencyReport delinquency(@PathVariable UUID c,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return collection.delinquency(c, asOf);
    }

    @GetMapping("/units/{unitId}/debt")
    @Operation(summary = "Demonstrativo de débitos da unidade, histórico de cobrança e acordos")
    public UnitDebt unitDebt(@PathVariable UUID c, @PathVariable UUID unitId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return collection.unitDebt(c, unitId, asOf);
    }

    @PostMapping("/units/{unitId}/notes")
    @Operation(summary = "Registra contato de cobrança")
    public ResponseEntity<NoteResponse> addNote(@PathVariable UUID c, @PathVariable UUID unitId, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(201).body(collection.addNote(c, unitId, request));
    }

    @GetMapping("/agreements")
    public List<AgreementResponse> agreements(@PathVariable UUID c) {
        return collection.agreements(c);
    }

    @PostMapping("/agreements")
    @Operation(summary = "Cria acordo de parcelamento (renegocia os débitos selecionados)")
    public ResponseEntity<AgreementResponse> createAgreement(@PathVariable UUID c, @Valid @RequestBody AgreementRequest request) {
        return ResponseEntity.status(201).body(collection.createAgreement(c, request));
    }

    @PostMapping("/agreements/{id}/cancel")
    public AgreementResponse cancelAgreement(@PathVariable UUID c, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return collection.cancelAgreement(c, id, request.reason());
    }
}
