package br.com.tekcondo.integration.zipcode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/zip-codes")
@Tag(name = "CEP")
public class ZipCodeController {

    private final ViaCepService service;

    public ZipCodeController(ViaCepService service) {
        this.service = service;
    }

    @GetMapping("/{zipCode:\\d{8}}")
    @Operation(summary = "Endereço do CEP (ViaCEP)", description = "204 quando o CEP não existe ou o ViaCEP não responde.")
    public ResponseEntity<ZipCodeAddress> find(@PathVariable String zipCode) {
        return service.find(zipCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
