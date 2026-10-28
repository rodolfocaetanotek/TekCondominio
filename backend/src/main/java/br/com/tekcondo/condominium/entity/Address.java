package br.com.tekcondo.condominium.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Address(
        @Column(name = "zip_code") String zipCode,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state) {
}
