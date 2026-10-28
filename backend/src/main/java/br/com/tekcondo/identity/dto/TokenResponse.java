package br.com.tekcondo.identity.dto;

import java.time.Instant;

/** O refresh token NÃO é retornado no corpo: vai somente em cookie HttpOnly. */
public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {

    public static TokenResponse bearer(String accessToken, Instant expiresAt) {
        return new TokenResponse(accessToken, "Bearer", expiresAt);
    }
}
