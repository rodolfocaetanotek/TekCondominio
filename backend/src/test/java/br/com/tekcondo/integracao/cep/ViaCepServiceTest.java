package br.com.tekcondo.integracao.cep;

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
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ViaCepService(builder);
    }

    @Test
    void retornaEnderecoEGuardaEmCache() {
        server.expect(requestTo("https://viacep.com.br/ws/01310100/json/"))
                .andRespond(withSuccess("""
                        {"cep":"01310-100","logradouro":"Avenida Paulista","complemento":"de 612 a 1510 - lado par",
                         "bairro":"Bela Vista","localidade":"São Paulo","uf":"SP"}
                        """, MediaType.APPLICATION_JSON));

        EnderecoCep esperado = new EnderecoCep("01310100", "Avenida Paulista", "Bela Vista", "São Paulo", "SP");
        assertThat(service.buscar("01310100")).contains(esperado);
        assertThat(service.buscar("01310100")).contains(esperado);
        server.verify();
    }

    @Test
    void vazioQuandoCepNaoExiste() {
        server.expect(requestTo("https://viacep.com.br/ws/99999999/json/"))
                .andRespond(withSuccess("{\"erro\": \"true\"}", MediaType.APPLICATION_JSON));

        assertThat(service.buscar("99999999")).isEmpty();
    }

    @Test
    void vazioQuandoProvedorFalha() {
        server.expect(requestTo("https://viacep.com.br/ws/01310100/json/"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(service.buscar("01310100")).isEmpty();
    }
}
