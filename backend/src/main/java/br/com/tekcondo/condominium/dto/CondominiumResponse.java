package br.com.tekcondo.condominium.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.entity.CondominiumType;

public record CondominiumResponse(
        UUID id,
        String name,
        String legalName,
        String cnpj,
        CondominiumType type,
        AddressDto address,
        String email,
        String phone,
        CondominiumStatus status,
        Instant inactivatedAt,
        String inactivationReason,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
