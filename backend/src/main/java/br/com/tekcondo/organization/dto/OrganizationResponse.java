package br.com.tekcondo.organization.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.organization.entity.Organization;

public record OrganizationResponse(UUID id, String name, String cnpj, String email, String phone, boolean active,
                                   Instant createdAt) {

    public static OrganizationResponse from(Organization o) {
        return new OrganizationResponse(o.getId(), o.getName(), o.getCnpj(), o.getEmail(), o.getPhone(), o.isActive(),
                o.getCreatedAt());
    }
}
