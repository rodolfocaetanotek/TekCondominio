package br.com.tekcondo.condominium.mapper;

import java.util.Locale;

import br.com.tekcondo.condominium.dto.AddressDto;
import br.com.tekcondo.condominium.dto.CondominiumResponse;
import br.com.tekcondo.condominium.dto.CondominiumSummary;
import br.com.tekcondo.condominium.entity.Address;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.shared.validation.BrazilianDocuments;
import org.springframework.stereotype.Component;

/** Conversão entidade ↔ DTO e normalização de entradas (máscaras removidas, textos aparados). */
@Component
public class CondominiumMapper {

    public CondominiumResponse toResponse(Condominium c) {
        Address a = c.getAddress();
        AddressDto address = a == null ? null
                : new AddressDto(a.zipCode(), a.street(), a.number(), a.complement(), a.district(), a.city(), a.state());
        return new CondominiumResponse(c.getId(), c.getName(), c.getLegalName(), c.getCnpj(), c.getType(), address,
                c.getEmail(), c.getPhone(), c.getStatus(), c.getInactivatedAt(), c.getInactivationReason(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    public CondominiumSummary toSummary(Condominium c) {
        Address a = c.getAddress();
        return new CondominiumSummary(c.getId(), c.getName(), c.getCnpj(), c.getType(),
                a == null ? null : a.city(), a == null ? null : a.state(), c.getStatus());
    }

    public Address toAddress(AddressDto dto) {
        if (dto == null) {
            return null;
        }
        Address address = new Address(BrazilianDocuments.digitsOnly(dto.zipCode()), trim(dto.street()),
                trim(dto.number()), trim(dto.complement()), trim(dto.district()), trim(dto.city()),
                dto.state() == null || dto.state().isBlank() ? null : dto.state().trim().toUpperCase(Locale.ROOT));
        boolean empty = address.zipCode() == null && address.street() == null && address.number() == null
                && address.complement() == null && address.district() == null && address.city() == null
                && address.state() == null;
        return empty ? null : address;
    }

    public static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
