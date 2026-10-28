package br.com.tekcondo.comum.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentosBrasileirosTest {

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-81", "11222333000181", "12.ABC.345/01DE-35", "12abc34501de35"})
    void aceitaCnpjNumericoEAlfanumericoValido(String cnpj) {
        assertThat(DocumentosBrasileiros.isCnpjValido(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-82", "00000000000000", "AAAAAAAAAAAA00", "12.ABC.345/01DE-36",
            "12ABC34501DEAB", "1122233300018", "", "11.222.333/0001-8X"})
    void rejeitaCnpjInvalido(String cnpj) {
        assertThat(DocumentosBrasileiros.isCnpjValido(cnpj)).isFalse();
    }

    @Test
    void normalizaMascaraEMaiusculasDoCnpj() {
        assertThat(DocumentosBrasileiros.normalizarCnpj(" 12.abc.345/01de-35 ")).isEqualTo("12ABC34501DE35");
        assertThat(DocumentosBrasileiros.normalizarCnpj("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(11) 98765-4321", "1134567890", "11 3456-7890"})
    void aceitaTelefonesValidos(String telefone) {
        assertThat(DocumentosBrasileiros.isTelefoneValido(telefone)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "(01) 98765-4321", "119876543210"})
    void rejeitaTelefonesInvalidos(String telefone) {
        assertThat(DocumentosBrasileiros.isTelefoneValido(telefone)).isFalse();
    }

    @Test
    void validaCep() {
        assertThat(DocumentosBrasileiros.isCepValido("01310-100")).isTrue();
        assertThat(DocumentosBrasileiros.isCepValido("01310100")).isTrue();
        assertThat(DocumentosBrasileiros.isCepValido("0131-0100")).isFalse();
        assertThat(DocumentosBrasileiros.isCepValido("1310100")).isFalse();
    }

    @Test
    void validaCpf() {
        assertThat(DocumentosBrasileiros.isCpfValido("529.982.247-25")).isTrue();
        assertThat(DocumentosBrasileiros.isCpfValido("11144477735")).isTrue();
        assertThat(DocumentosBrasileiros.isCpfValido("111.444.777-36")).isFalse();
        assertThat(DocumentosBrasileiros.isCpfValido("000.000.000-00")).isFalse();
        assertThat(DocumentosBrasileiros.isCpfOuCnpjValido("12.ABC.345/01DE-35")).isTrue();
        assertThat(DocumentosBrasileiros.normalizarDocumento("529.982.247-25")).isEqualTo("52998224725");
    }

    @Test
    void politicaDeSenha() {
        assertThat(SenhaForte.Validator.isForte("SenhaForte123")).isTrue();
        assertThat(SenhaForte.Validator.isForte("curta1")).isFalse();
        assertThat(SenhaForte.Validator.isForte("semnumeroslongos")).isFalse();
        assertThat(SenhaForte.Validator.isForte("12345678901")).isFalse();
    }
}
