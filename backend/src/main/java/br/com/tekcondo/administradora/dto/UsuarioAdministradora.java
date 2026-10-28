package br.com.tekcondo.administradora.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UsuarioAdministradora(UUID id, String nome, String email, boolean ativo, boolean convitePendente,
                                    Instant ultimoLoginEm, List<String> papeis) {
}
