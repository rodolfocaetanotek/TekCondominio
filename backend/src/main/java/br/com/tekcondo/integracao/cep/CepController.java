package br.com.tekcondo.integracao.cep;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ceps")
@Tag(name = "CEP")
public class CepController {

    private final ViaCepService service;

    public CepController(ViaCepService service) {
        this.service = service;
    }

    @GetMapping("/{cep:\\d{8}}")
    @Operation(summary = "Endereço do CEP (ViaCEP)", description = "204 quando o CEP não existe ou o ViaCEP não responde.")
    public ResponseEntity<EnderecoCep> buscar(@PathVariable String cep) {
        return service.buscar(cep)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
