package br.com.tekcondo.condominio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Endereco(
        @Column(name = "zip_code") String cep,
        @Column(name = "street") String logradouro,
        @Column(name = "number") String numero,
        @Column(name = "complement") String complemento,
        @Column(name = "district") String bairro,
        @Column(name = "city") String cidade,
        @Column(name = "state") String uf) {
}
