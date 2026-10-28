package br.com.tekcondo.structure.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.structure.entity.Unit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class StructureDtos {

    private StructureDtos() {
    }

    public record BuildingRequest(
            @NotBlank(message = "Informe o nome do bloco.") @Size(max = 80, message = "Máximo de 80 caracteres.") String name,
            @Size(max = 255) String description) {
    }

    public record BuildingResponse(UUID id, String name, String description, boolean active, long activeUnits) {
    }

    public record UnitRequest(
            UUID buildingId,
            @NotBlank(message = "Informe a identificação da unidade.") @Size(max = 30, message = "Máximo de 30 caracteres.") String identifier,
            @NotNull(message = "Informe o tipo.") Unit.Type type,
            @Size(max = 10) String floor,
            @Positive(message = "A área deve ser positiva.") @Digits(integer = 8, fraction = 2, message = "Área com no máximo 2 casas decimais.") BigDecimal area,
            @DecimalMin(value = "0", message = "A fração ideal não pode ser negativa.")
            @DecimalMax(value = "100", message = "A fração ideal não pode passar de 100%.")
            @Digits(integer = 3, fraction = 6, message = "Fração ideal com no máximo 6 casas decimais.") BigDecimal idealFraction,
            @Size(max = 500) String notes,
            Long version) {
    }

    public record UnitResponse(UUID id, UUID buildingId, String buildingName, String identifier, String label,
                               Unit.Type type, String floor, BigDecimal area, BigDecimal idealFraction, String notes,
                               boolean active, Long version, String ownerName, String financialResponsibleName) {
    }

    /** Situação das frações ideais: o rateio por fração exige soma de 100% nas unidades ativas. */
    public record FractionSummary(BigDecimal total, long activeUnits, boolean balanced, BigDecimal difference) {
    }

    public record ImportError(int line, String message) {
    }

    public record ImportResult(boolean dryRun, int lines, int buildingsCreated, int unitsCreated, int peopleCreated,
                               int peopleReused, List<ImportError> errors) {
    }
}
