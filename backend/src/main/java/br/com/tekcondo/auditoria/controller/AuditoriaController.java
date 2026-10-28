package br.com.tekcondo.auditoria.controller;

import java.time.Instant;
import java.util.Map;

import br.com.tekcondo.auditoria.dto.RegistroAuditoriaResponse;
import br.com.tekcondo.auditoria.service.ConsultaAuditoriaService;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoria")
public class AuditoriaController {

    private static final Map<String, String> ORDENS = Map.of("ocorridoEm", "ocorridoEm", "acao", "acao");

    private final ConsultaAuditoriaService service;

    public AuditoriaController(ConsultaAuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Trilha de auditoria da organização do usuário")
    public PaginaResponse<RegistroAuditoriaResponse> listar(
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String tipoEntidade,
            @RequestParam(required = false) String idEntidade,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho,
            @RequestParam(required = false) String ordem) {
        return service.listar(acao, tipoEntidade, idEntidade, de, ate,
                PaginaRequests.of(pagina, tamanho, ordem, ORDENS, Sort.by(Sort.Direction.DESC, "ocorridoEm")));
    }
}
