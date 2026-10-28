package br.com.tekcondo.structure.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.web.PageRequests;
import br.com.tekcondo.shared.web.PageResponse;
import br.com.tekcondo.structure.dto.StructureDtos.BuildingRequest;
import br.com.tekcondo.structure.dto.StructureDtos.BuildingResponse;
import br.com.tekcondo.structure.dto.StructureDtos.FractionSummary;
import br.com.tekcondo.structure.dto.StructureDtos.ImportResult;
import br.com.tekcondo.structure.dto.StructureDtos.UnitRequest;
import br.com.tekcondo.structure.dto.StructureDtos.UnitResponse;
import br.com.tekcondo.structure.service.StructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/condominiums/{condominiumId}")
@Tag(name = "Estrutura: blocos e unidades")
public class StructureController {

    private static final Map<String, String> UNIT_SORTS = Map.of("identifier", "identifier", "idealFraction", "idealFraction",
            "type", "type");
    private static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024;

    private final StructureService service;

    public StructureController(StructureService service) {
        this.service = service;
    }

    @GetMapping("/buildings")
    @Operation(summary = "Lista blocos/torres")
    public List<BuildingResponse> buildings(@PathVariable UUID condominiumId) {
        return service.buildings(condominiumId);
    }

    @PostMapping("/buildings")
    @Operation(summary = "Cadastra bloco/torre")
    public ResponseEntity<BuildingResponse> createBuilding(@PathVariable UUID condominiumId,
                                                           @Valid @RequestBody BuildingRequest request) {
        return ResponseEntity.status(201).body(service.createBuilding(condominiumId, request));
    }

    @PutMapping("/buildings/{id}")
    public BuildingResponse updateBuilding(@PathVariable UUID condominiumId, @PathVariable UUID id,
                                           @Valid @RequestBody BuildingRequest request) {
        return service.updateBuilding(condominiumId, id, request);
    }

    @PostMapping("/buildings/{id}/activate")
    public BuildingResponse activateBuilding(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        return service.setBuildingActive(condominiumId, id, true);
    }

    @PostMapping("/buildings/{id}/deactivate")
    public BuildingResponse deactivateBuilding(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        return service.setBuildingActive(condominiumId, id, false);
    }

    @GetMapping("/units")
    @Operation(summary = "Lista unidades com proprietário e responsável financeiro vigentes")
    public PageResponse<UnitResponse> units(@PathVariable UUID condominiumId,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) UUID buildingId,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size,
                                            @RequestParam(required = false) String sort) {
        return service.units(condominiumId, search, buildingId, active,
                PageRequests.of(page, size, sort, UNIT_SORTS, Sort.by("identifier").and(Sort.by("id"))));
    }

    @GetMapping("/units/fraction-summary")
    @Operation(summary = "Soma das frações ideais das unidades ativas (deve totalizar 100%)")
    public FractionSummary fractionSummary(@PathVariable UUID condominiumId) {
        return service.fractionSummary(condominiumId);
    }

    @GetMapping("/units/{id}")
    public UnitResponse unit(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        return service.unit(condominiumId, id);
    }

    @PostMapping("/units")
    @Operation(summary = "Cadastra unidade")
    public ResponseEntity<UnitResponse> createUnit(@PathVariable UUID condominiumId, @Valid @RequestBody UnitRequest request) {
        return ResponseEntity.status(201).body(service.createUnit(condominiumId, request));
    }

    @PutMapping("/units/{id}")
    public UnitResponse updateUnit(@PathVariable UUID condominiumId, @PathVariable UUID id,
                                   @Valid @RequestBody UnitRequest request) {
        return service.updateUnit(condominiumId, id, request);
    }

    @PostMapping("/units/{id}/activate")
    public UnitResponse activateUnit(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        return service.setUnitActive(condominiumId, id, true);
    }

    @PostMapping("/units/{id}/deactivate")
    public UnitResponse deactivateUnit(@PathVariable UUID condominiumId, @PathVariable UUID id) {
        return service.setUnitActive(condominiumId, id, false);
    }

    @PostMapping(value = "/units/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa unidades e proprietários de planilha CSV",
            description = "Colunas: bloco; unidade; tipo; fracao; area; proprietario; documento; email; telefone. "
                    + "Use dryRun=true para validar sem gravar. Com qualquer erro, nada é gravado.")
    public ImportResult importUnits(@PathVariable UUID condominiumId, @RequestPart("file") MultipartFile file,
                                    @RequestParam(defaultValue = "false") boolean dryRun) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_IMPORT_BYTES) {
            throw ApiException.badRequest("INVALID_FILE", "Envie um arquivo CSV de até 2 MB.");
        }
        return service.importUnits(condominiumId, file.getBytes(), dryRun);
    }
}
