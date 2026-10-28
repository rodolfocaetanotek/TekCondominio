package br.com.tekcondo.acesso.dto;

import java.time.Instant;

/** O refresh token NÃO é retornado no corpo: vai somente em cookie HttpOnly. */
public record TokenResponse(String accessToken, String tokenType, Instant expiraEm) {

    public static TokenResponse bearer(String accessToken, Instant expiraEm) {
        return new TokenResponse(accessToken, "Bearer", expiraEm);
    }
}
