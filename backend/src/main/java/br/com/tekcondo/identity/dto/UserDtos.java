package br.com.tekcondo.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.identity.entity.RoleScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs da gestão de usuários e papéis. */
public final class UserDtos {

    private UserDtos() {
    }

    public record Assignment(UUID id, String roleCode, String roleName, RoleScope scope, UUID condominiumId,
                             String condominiumName) {
    }

    public record Summary(UUID id, String name, String email, boolean active, boolean pendingInvite,
                          Instant lastLoginAt, List<String> roles) {
    }

    public record Detail(UUID id, String name, String email, boolean active, boolean pendingInvite,
                         Instant lastLoginAt, Instant createdAt, Long version, List<Assignment> assignments) {
    }

    public record AssignmentRequest(
            @NotBlank(message = "Informe o papel.") @Size(max = 40) String roleCode,
            UUID condominiumId) {
    }

    public record CreateRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Máximo de 150 caracteres.") String name,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @NotNull(message = "Informe o papel inicial.") @Valid AssignmentRequest assignment) {
    }

    public record UpdateRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Máximo de 150 caracteres.") String name,
            @NotNull(message = "Informe a versão do registro.") Long version) {
    }

    public record RoleResponse(String code, String name, String description, RoleScope scope, Set<Permission> permissions) {
    }
}
