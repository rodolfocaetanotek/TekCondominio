package br.com.tekcondo.condominium.dto;

import java.util.UUID;

import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.entity.CondominiumType;

/** Linha da listagem de condomínios. */
public record CondominiumSummary(UUID id, String name, String cnpj, CondominiumType type, String city, String state,
                                 CondominiumStatus status) {
}
