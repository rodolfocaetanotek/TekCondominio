package br.com.tekcondo.portal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.comunicado.ComunicadoService;
import br.com.tekcondo.documento.controller.DocumentoController;
import br.com.tekcondo.documento.service.DocumentoService;
import br.com.tekcondo.ocorrencia.OcorrenciaService;
import br.com.tekcondo.portaria.PortariaService;
import br.com.tekcondo.reserva.ReservaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal")
@Tag(name = "Portal do morador")
public class PortalController {

    private final PortalService service;

    public PortalController(PortalService service) {
        this.service = service;
    }

    @GetMapping("/perfil")
    public PortalService.PerfilPortal perfil() {
        return service.perfil();
    }

    @GetMapping("/{idCondominio}/cobrancas")
    public List<PortalService.CobrancasUnidade> cobrancas(@PathVariable UUID idCondominio) {
        return service.cobrancas(idCondominio);
    }

    @GetMapping("/{idCondominio}/comunicados")
    public List<ComunicadoService.ComunicadoResponse> comunicados(@PathVariable UUID idCondominio) {
        return service.comunicados(idCondominio);
    }

    @PostMapping("/{idCondominio}/comunicados/{id}/marcar-lido")
    public ResponseEntity<Void> marcarComunicadoLido(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        service.marcarComunicadoLido(idCondominio, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{idCondominio}/documentos")
    public List<DocumentoService.DocumentoResponse> documentos(@PathVariable UUID idCondominio) {
        return service.documentos(idCondominio);
    }

    @GetMapping("/{idCondominio}/documentos/{id}/baixar")
    public ResponseEntity<byte[]> baixarDocumento(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return DocumentoController.comoAnexo(service.baixarDocumento(idCondominio, id));
    }

    @GetMapping("/{idCondominio}/ocorrencias")
    public List<OcorrenciaService.Resumo> ocorrencias(@PathVariable UUID idCondominio) {
        return service.ocorrencias(idCondominio);
    }

    @GetMapping("/{idCondominio}/ocorrencias/{id}")
    public OcorrenciaService.Detalhe ocorrencia(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.ocorrencia(idCondominio, id);
    }

    @PostMapping("/{idCondominio}/ocorrencias")
    public ResponseEntity<OcorrenciaService.Detalhe> abrirOcorrencia(@PathVariable UUID idCondominio,
                                                                     @Valid @RequestBody OcorrenciaService.CadastroRequest request) {
        return ResponseEntity.status(201).body(service.abrirOcorrencia(idCondominio, request));
    }

    @PostMapping("/{idCondominio}/ocorrencias/{id}/interacoes")
    public OcorrenciaService.Detalhe comentarOcorrencia(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                        @Valid @RequestBody OcorrenciaService.ComentarioRequest request) {
        return service.comentarOcorrencia(idCondominio, id, request.mensagem());
    }

    @GetMapping("/{idCondominio}/assembleias")
    public List<PortalService.AssembleiaPortal> assembleias(@PathVariable UUID idCondominio) {
        return service.assembleias(idCondominio);
    }

    @GetMapping("/{idCondominio}/areas-comuns")
    public List<ReservaService.AreaComumResponse> areasComuns(@PathVariable UUID idCondominio) {
        return service.areasComuns(idCondominio);
    }

    @GetMapping("/{idCondominio}/areas-comuns/{idArea}/ocupacao")
    public List<ReservaService.Horario> ocupacaoArea(@PathVariable UUID idCondominio, @PathVariable UUID idArea,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.ocupacaoArea(idCondominio, idArea, de, ate);
    }

    @GetMapping("/{idCondominio}/reservas")
    public List<ReservaService.ReservaResponse> minhasReservas(@PathVariable UUID idCondominio) {
        return service.minhasReservas(idCondominio);
    }

    @PostMapping("/{idCondominio}/reservas")
    public ResponseEntity<ReservaService.ReservaResponse> reservar(@PathVariable UUID idCondominio,
                                                                   @Valid @RequestBody ReservaService.ReservaRequest request) {
        return ResponseEntity.status(201).body(service.reservar(idCondominio, request));
    }

    @PostMapping("/{idCondominio}/reservas/{id}/cancelar")
    public ReservaService.ReservaResponse cancelarReserva(@PathVariable UUID idCondominio, @PathVariable UUID id,
                                                          @Valid @RequestBody ReservaService.CancelamentoRequest request) {
        return service.cancelarReserva(idCondominio, id, request.motivo());
    }

    @GetMapping("/{idCondominio}/visitantes")
    public List<PortariaService.AutorizacaoResponse> autorizacoesVisita(@PathVariable UUID idCondominio) {
        return service.autorizacoesVisita(idCondominio);
    }

    @PostMapping("/{idCondominio}/visitantes")
    public ResponseEntity<PortariaService.AutorizacaoResponse> autorizarVisita(@PathVariable UUID idCondominio,
                                                                               @Valid @RequestBody PortariaService.AutorizacaoRequest request) {
        return ResponseEntity.status(201).body(service.autorizarVisita(idCondominio, request));
    }

    @PostMapping("/{idCondominio}/visitantes/{id}/cancelar")
    public PortariaService.AutorizacaoResponse cancelarAutorizacaoVisita(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.cancelarAutorizacaoVisita(idCondominio, id);
    }

    @GetMapping("/{idCondominio}/calendario")
    public List<PortalService.EventoCalendario> calendario(@PathVariable UUID idCondominio) {
        return service.calendario(idCondominio);
    }
}
