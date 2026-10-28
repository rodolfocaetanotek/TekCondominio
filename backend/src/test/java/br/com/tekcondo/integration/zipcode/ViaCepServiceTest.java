package br.com.tekcondo.integration.zipcode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ViaCepServiceTest {

    MockRestServiceServer server;
    ViaCepService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ViaCepService(builder);
    }

    @Test
    void returnsAddressAndCachesIt() {
        server.expect(requestTo("https://viacep.com.br/ws/01310100/json/"))
                .andRespond(withSuccess("""
                        {"cep":"01310-100","logradouro":"Avenida Paulista","complemento":"de 612 a 1510 - lado par",
                         "bairro":"Bela Vista","localidade":"São Paulo","uf":"SP"}
                        """, MediaType.APPLICATION_JSON));

        ZipCodeAddress expected = new ZipCodeAddress("01310100", "Avenida Paulista", "Bela Vista", "São Paulo", "SP");
        assertThat(service.find("01310100")).contains(expected);
        assertThat(service.find("01310100")).contains(expected);
        server.verify();
    }

    @Test
    void emptyWhenZipCodeDoesNotExist() {
        server.expect(requestTo("https://viacep.com.br/ws/99999999/json/"))
                .andRespond(withSuccess("{\"erro\": \"true\"}", MediaType.APPLICATION_JSON));

        assertThat(service.find("99999999")).isEmpty();
    }

    @Test
    void emptyWhenProviderFails() {
        server.expect(requestTo("https://viacep.com.br/ws/01310100/json/"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(service.find("01310100")).isEmpty();
    }
}
