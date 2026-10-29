package br.com.tekcondo.assembleia;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.financeiro.dto.FinanceiroDtos.MotivoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/condominios/{idCondominio}/assembleias")
@Tag(name = "Assembleias e atas")
public class AssembleiaController {

    private final AssembleiaService service;

    public AssembleiaController(AssembleiaService service) {
        this.service = service;
    }

    @GetMapping
    public List<AssembleiaService.Resumo> listar(@PathVariable UUID idCondominio) {
        return service.listar(idCondominio);
    }

    @GetMapping("/{id}")
    public AssembleiaService.Detalhe buscar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.buscar(idCondominio, id);
    }

    @PostMapping
    public ResponseEntity<AssembleiaService.Detalhe> cadastrar(@PathVariable UUID idCondominio,
                                                               @Valid @RequestBody AssembleiaService.AssembleiaRequest request) {
        return ResponseEntity.status(201).body(service.cadastrar(idCondominio, request));
    }

    @PutMapping("/{id}")
    public AssembleiaService.Detalhe atualizar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                               @Valid @RequestBody AssembleiaService.AssembleiaRequest request) {
        return service.atualizar(idCondominio, id, request);
    }

    @PostMapping("/{id}/convocar")
    @Operation(summary = "Convoca: publica comunicado com a pauta para todos os condôminos")
    public AssembleiaService.Detalhe convocar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.convocar(idCondominio, id);
    }

    @PostMapping("/{id}/iniciar")
    public AssembleiaService.Detalhe iniciar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.iniciar(idCondominio, id);
    }

    @PostMapping("/{id}/encerrar")
    public AssembleiaService.Detalhe encerrar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.encerrar(idCondominio, id);
    }

    @PostMapping("/{id}/cancelar")
    public AssembleiaService.Detalhe cancelar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                              @Valid @RequestBody MotivoRequest request) {
        return service.cancelar(idCondominio, id, request.motivo());
    }

    @PostMapping("/{id}/presencas")
    @Operation(summary = "Registra presença da unidade (pessoalmente ou por procuração)")
    public AssembleiaService.Detalhe registrarPresenca(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                       @Valid @RequestBody AssembleiaService.PresencaRequest request) {
        return service.registrarPresenca(idCondominio, id, request);
    }

    @DeleteMapping("/{id}/presencas/{idPresenca}")
    public AssembleiaService.Detalhe removerPresenca(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                     @PathVariable UUID idPresenca) {
        return service.removerPresenca(idCondominio, id, idPresenca);
    }

    @PostMapping("/{id}/votos")
    @Operation(summary = "Registra o voto de uma unidade presente em um item deliberativo")
    public AssembleiaService.Detalhe votar(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                           @Valid @RequestBody AssembleiaService.VotoRequest request) {
        return service.votar(idCondominio, id, request);
    }

    @PutMapping("/{id}/pauta/{idItemPauta}/deliberacao")
    public AssembleiaService.Detalhe deliberar(@PathVariable UUID idCondominio, @PathVariable UUID id, @PathVariable UUID idItemPauta,
                                               @Valid @RequestBody AssembleiaService.DeliberacaoRequest request) {
        return service.deliberar(idCondominio, id, idItemPauta, request);
    }

    @PutMapping("/{id}/ata")
    public AssembleiaService.Detalhe registrarAta(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                  @Valid @RequestBody AssembleiaService.AtaRequest request) {
        return service.registrarAta(idCondominio, id, request.ata());
    }

    @GetMapping("/{id}/rascunho-ata")
    @Operation(summary = "Rascunho da ata gerado a partir dos dados registrados")
    public Map<String, String> rascunhoAta(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return Map.of("ata", service.rascunhoAta(idCondominio, id));
    }
}
