package br.com.tekcondo.people.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.people.entity.Occupancy;
import br.com.tekcondo.people.entity.Person;
import br.com.tekcondo.comum.validation.CpfCnpj;
import br.com.tekcondo.comum.validation.Telefone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PeopleDtos {

    private PeopleDtos() {
    }

    public record PersonRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Máximo de 150 caracteres.") String name,
            @NotNull(message = "Informe se é pessoa física ou jurídica.") Person.Kind kind,
            @CpfCnpj String document,
            @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @Telefone String phone,
            @Telefone String secondaryPhone,
            @Size(max = 500) String notes,
            Long version) {
    }

    public record PersonSummary(UUID id, String name, Person.Kind kind, String document, String email, String phone,
                                boolean active, List<String> units) {
    }

    public record PersonDetail(UUID id, String name, Person.Kind kind, String document, String email, String phone,
                               String secondaryPhone, String notes, boolean active, Long version, boolean portalUser,
                               List<OccupancyResponse> occupancies) {
    }

    public record OccupancyRequest(
            @NotNull(message = "Informe a unidade.") UUID unitId,
            @NotNull(message = "Informe a pessoa.") UUID personId,
            @NotNull(message = "Informe o vínculo.") Occupancy.Role role,
            boolean financialResponsible,
            @NotNull(message = "Informe a data de início.") LocalDate startDate) {
    }

    public record EndOccupancyRequest(
            @NotNull(message = "Informe a data de encerramento.") LocalDate endDate,
            @Size(max = 255) String reason) {
    }

    public record OccupancyResponse(UUID id, UUID condominiumId, UUID unitId, String unitLabel, UUID personId,
                                    String personName, Occupancy.Role role, boolean financialResponsible,
                                    LocalDate startDate, LocalDate endDate, String endReason, boolean current) {

        public static OccupancyResponse from(Occupancy o) {
            return new OccupancyResponse(o.getId(), o.getUnit().getCondominiumId(), o.getUnit().getId(),
                    o.getUnit().label(), o.getPerson().getId(), o.getPerson().getName(), o.getRole(),
                    o.isFinancialResponsible(), o.getStartDate(), o.getEndDate(), o.getEndReason(), o.isCurrent());
        }
    }
}
