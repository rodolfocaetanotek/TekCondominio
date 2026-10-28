package br.com.tekcondo.billing.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.billing.service.BankReturnService;
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
@RequestMapping("/api/v1/condominiums/{c}/billing")
@Tag(name = "Cobrança — retorno bancário (CNAB 240)")
public class BankReturnController {

    private final BankReturnService service;

    public BankReturnController(BankReturnService service) {
        this.service = service;
    }

    @PostMapping("/bank-returns")
    @Operation(summary = "Processa arquivo de retorno CNAB 240 e baixa automaticamente as cobranças liquidadas")
    public ResponseEntity<BankReturnService.FileSummary> process(@PathVariable UUID c, @RequestParam UUID bankAccountId,
                                                                 @RequestPart("file") MultipartFile file) {
        try {
            return ResponseEntity.status(201).body(service.process(c, bankAccountId, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            throw ApiException.requisicaoInvalida("UPLOAD_FAILED", "Não foi possível ler o arquivo enviado.");
        }
    }

    @GetMapping("/bank-returns")
    public List<BankReturnService.FileSummary> files(@PathVariable UUID c) {
        return service.files(c);
    }

    @GetMapping("/bank-returns/{id}/items")
    public List<BankReturnService.ItemView> items(@PathVariable UUID c, @PathVariable UUID id) {
        return service.items(c, id);
    }

    @GetMapping("/bank-return-items/pending")
    @Operation(summary = "Liquidações que não puderam ser baixadas automaticamente")
    public List<BankReturnService.ItemView> pending(@PathVariable UUID c) {
        return service.pending(c);
    }

    @PostMapping("/bank-return-items/{id}/settle")
    public BankReturnService.ItemView settle(@PathVariable UUID c, @PathVariable UUID id,
                                             @Valid @RequestBody BankReturnService.SettleRequest request) {
        return service.settleManually(c, id, request);
    }

    @PostMapping("/bank-return-items/{id}/ignore")
    public BankReturnService.ItemView ignore(@PathVariable UUID c, @PathVariable UUID id,
                                             @Valid @RequestBody BankReturnService.IgnoreRequest request) {
        return service.ignore(c, id, request);
    }
}
