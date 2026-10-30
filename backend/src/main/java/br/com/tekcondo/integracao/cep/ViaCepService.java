package br.com.tekcondo.integracao.cep;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Consulta de endereço por CEP no ViaCEP. CEP inexistente ou provedor indisponível resultam em vazio:
 * o formulário segue com o preenchimento manual. Os endereços encontrados ficam em cache, CEP não muda.
 */
@Service
public class ViaCepService {

    private static final Logger log = LoggerFactory.getLogger(ViaCepService.class);
    private static final int TAMANHO_CACHE = 10_000;

    private final RestClient client;
    private final Map<String, EnderecoCep> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, EnderecoCep> maisAntigo) {
                    return size() > TAMANHO_CACHE;
                }
            });

    public ViaCepService(RestClient.Builder builder) {
        this.client = builder.baseUrl("https://viacep.com.br").build();
    }

    public Optional<EnderecoCep> buscar(String cep) {
        EnderecoCep emCache = cache.get(cep);
        if (emCache != null) {
            return Optional.of(emCache);
        }
        try {
            RespostaViaCep r = client.get().uri("/ws/{cep}/json/", cep).retrieve().body(RespostaViaCep.class);
            if (r == null || r.erro() != null) {
                return Optional.empty();
            }
            EnderecoCep endereco = new EnderecoCep(cep, r.logradouro(), r.bairro(), r.localidade(), r.uf());
            cache.put(cep, endereco);
            return Optional.of(endereco);
        } catch (RestClientException e) {
            log.warn("ViaCEP indisponível: {}", e.getMessage());
            return Optional.empty();
        }
    }

    record RespostaViaCep(String logradouro, String bairro, String localidade, String uf, String erro) {
    }
}
