package br.com.tekcondo.integration.zipcode;

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
    private static final int CACHE_SIZE = 10_000;

    private final RestClient client;
    private final Map<String, ZipCodeAddress> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ZipCodeAddress> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public ViaCepService(RestClient.Builder builder) {
        this.client = builder.baseUrl("https://viacep.com.br").build();
    }

    public Optional<ZipCodeAddress> find(String zipCode) {
        ZipCodeAddress cached = cache.get(zipCode);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            Response r = client.get().uri("/ws/{cep}/json/", zipCode).retrieve().body(Response.class);
            if (r == null || r.erro() != null) {
                return Optional.empty();
            }
            ZipCodeAddress address = new ZipCodeAddress(zipCode, r.logradouro(), r.bairro(), r.localidade(), r.uf());
            cache.put(zipCode, address);
            return Optional.of(address);
        } catch (RestClientException e) {
            log.warn("ViaCEP indisponível: {}", e.getMessage());
            return Optional.empty();
        }
    }

    record Response(String logradouro, String bairro, String localidade, String uf, String erro) {
    }
}
