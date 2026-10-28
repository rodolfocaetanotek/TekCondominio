package br.com.tekcondo.cobranca.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.cobranca.service.RetornoBancarioService;
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
@RequestMapping("/api/v1/condominios/{idCondominio}/cobranca")
@Tag(name = "Cobrança — retorno bancário (CNAB 240)")
public class RetornoBancarioController {

    private final RetornoBancarioService service;

    public RetornoBancarioController(RetornoBancarioService service) {
        this.service = service;
    }

    @PostMapping("/retornos-bancarios")
    @Operation(summary = "Processa arquivo de retorno CNAB 240 e baixa automaticamente as cobranças liquidadas")
    public ResponseEntity<RetornoBancarioService.ResumoArquivo> processar(@PathVariable UUID idCondominio,
                                                                          @RequestParam UUID idContaBancaria,
                                                                          @RequestPart("arquivo") MultipartFile arquivo) {
        try {
            return ResponseEntity.status(201).body(service.processar(idCondominio, idContaBancaria, arquivo.getOriginalFilename(),
                    arquivo.getBytes()));
        } catch (IOException e) {
            throw ApiException.requisicaoInvalida("UPLOAD_FAILED", "Não foi possível ler o arquivo enviado.");
        }
    }

    @GetMapping("/retornos-bancarios")
    public List<RetornoBancarioService.ResumoArquivo> arquivos(@PathVariable UUID idCondominio) {
        return service.arquivos(idCondominio);
    }

    @GetMapping("/retornos-bancarios/{id}/itens")
    public List<RetornoBancarioService.ItemRetornoResponse> itens(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.itens(idCondominio, id);
    }

    @GetMapping("/itens-retorno/pendentes")
    @Operation(summary = "Liquidações que não puderam ser baixadas automaticamente")
    public List<RetornoBancarioService.ItemRetornoResponse> pendentes(@PathVariable UUID idCondominio) {
        return service.pendentes(idCondominio);
    }

    @PostMapping("/itens-retorno/{id}/baixar")
    public RetornoBancarioService.ItemRetornoResponse baixar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                             @Valid @RequestBody RetornoBancarioService.BaixarItemRequest request) {
        return service.baixarManualmente(idCondominio, id, request);
    }

    @PostMapping("/itens-retorno/{id}/ignorar")
    public RetornoBancarioService.ItemRetornoResponse ignorar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                              @Valid @RequestBody RetornoBancarioService.IgnorarItemRequest request) {
        return service.ignorar(idCondominio, id, request);
    }
}
