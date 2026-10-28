package br.com.tekcondo.comum.security;

import java.util.List;

import br.com.tekcondo.comum.config.TekCondoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API stateless com JWT Bearer. CSRF desabilitado porque o token não é enviado automaticamente pelo navegador;
 * os endpoints baseados em cookie (/auth/refresh e /auth/logout) exigem o cabeçalho {@code X-TekCondo-Client},
 * o que força preflight CORS em chamadas de outras origens (defesa contra CSRF), além de SameSite=Strict.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String ROLE_ADMINISTRADOR_PLATAFORMA = "ROLE_PLATFORM_ADMIN";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemSecurityHandlers handlers) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout", "/api/v1/auth/senha/esqueci", "/api/v1/auth/senha/token",
                                "/api/v1/auth/senha/redefinir").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/administradoras/**").hasAuthority(ROLE_ADMINISTRADOR_PLATAFORMA)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(source -> {
                            boolean administradorPlataforma = Boolean.TRUE.equals(
                                    source.getClaimAsBoolean(UsuarioAtualProvider.CLAIM_ADMINISTRADOR_PLATAFORMA));
                            return new JwtAuthenticationToken(source,
                                    administradorPlataforma ? List.of(new SimpleGrantedAuthority(ROLE_ADMINISTRADOR_PLATAFORMA)) : List.of());
                        }))
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .headers(h -> h
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(TekCondoProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origens = props.security().corsAllowedOrigins();
        if (!origens.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origens);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-TekCondo-Client", "X-Request-Id"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }
}
