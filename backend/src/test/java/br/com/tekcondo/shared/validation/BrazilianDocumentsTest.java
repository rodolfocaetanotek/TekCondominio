package br.com.tekcondo.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrazilianDocumentsTest {

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-81", "11222333000181", "12.ABC.345/01DE-35", "12abc34501de35"})
    void acceptsValidNumericAndAlphanumericCnpj(String cnpj) {
        assertThat(BrazilianDocuments.isValidCnpj(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-82", "00000000000000", "AAAAAAAAAAAA00", "12.ABC.345/01DE-36",
            "12ABC34501DEAB", "1122233300018", "", "11.222.333/0001-8X"})
    void rejectsInvalidCnpj(String cnpj) {
        assertThat(BrazilianDocuments.isValidCnpj(cnpj)).isFalse();
    }

    @Test
    void normalizesCnpjMaskAndCase() {
        assertThat(BrazilianDocuments.normalizeCnpj(" 12.abc.345/01de-35 ")).isEqualTo("12ABC34501DE35");
        assertThat(BrazilianDocuments.normalizeCnpj("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(11) 98765-4321", "1134567890", "11 3456-7890"})
    void acceptsValidPhones(String phone) {
        assertThat(BrazilianDocuments.isValidPhone(phone)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "(01) 98765-4321", "119876543210"})
    void rejectsInvalidPhones(String phone) {
        assertThat(BrazilianDocuments.isValidPhone(phone)).isFalse();
    }

    @Test
    void validatesZipCode() {
        assertThat(BrazilianDocuments.isValidZipCode("01310-100")).isTrue();
        assertThat(BrazilianDocuments.isValidZipCode("01310100")).isTrue();
        assertThat(BrazilianDocuments.isValidZipCode("0131-0100")).isFalse();
        assertThat(BrazilianDocuments.isValidZipCode("1310100")).isFalse();
    }

    @Test
    void validatesCpf() {
        assertThat(BrazilianDocuments.isValidCpf("529.982.247-25")).isTrue();
        assertThat(BrazilianDocuments.isValidCpf("11144477735")).isTrue();
        assertThat(BrazilianDocuments.isValidCpf("111.444.777-36")).isFalse();
        assertThat(BrazilianDocuments.isValidCpf("000.000.000-00")).isFalse();
        assertThat(BrazilianDocuments.isValidCpfOrCnpj("12.ABC.345/01DE-35")).isTrue();
        assertThat(BrazilianDocuments.normalizeDocument("529.982.247-25")).isEqualTo("52998224725");
    }

    @Test
    void passwordPolicy() {
        assertThat(StrongPassword.Validator.isStrong("SenhaForte123")).isTrue();
        assertThat(StrongPassword.Validator.isStrong("curta1")).isFalse();
        assertThat(StrongPassword.Validator.isStrong("semnumeroslongos")).isFalse();
        assertThat(StrongPassword.Validator.isStrong("12345678901")).isFalse();
    }
}
