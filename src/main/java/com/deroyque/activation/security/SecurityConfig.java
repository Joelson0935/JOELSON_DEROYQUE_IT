package com.deroyque.activation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança: a API é um OAuth2 Resource Server que valida JWTs.
 *
 * <p>O que é validado no JWT (pelo Spring, a partir do {@code issuer-uri}/{@code jwk-set-uri}):
 * assinatura (via JWKS do issuer), emissor ({@code iss}), expiração ({@code exp}) e, quando
 * configurado, a audiência. As chaves públicas são obtidas do endpoint JWKS do Authorization
 * Server e cacheadas pelo Spring.
 *
 * <p>Autorização por escopo (via {@code @PreAuthorize} nos controllers):
 * {@code activation.write} para criar e {@code activation.read} para consultar. Os escopos do
 * claim {@code scope}/{@code scp} viram authorities com prefixo {@code SCOPE_}.
 *
 * <p>Comunicação máquina-a-máquina usa o fluxo <b>Client Credentials</b> do OAuth 2.0: cada
 * sistema consumidor tem um client_id/secret e obtém um access token do Authorization Server;
 * este serviço apenas valida o token, sem gerenciar usuários.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ProblemDetailSecurityHandlers securityHandlers;

    public SecurityConfig(ProblemDetailSecurityHandlers securityHandlers) {
        this.securityHandlers = securityHandlers;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API stateless com tokens: CSRF não se aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints de observabilidade liberados para probes.
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // Emissor de token de desenvolvimento (controller só existe no profile 'local').
                        .requestMatchers("/dev/token").permitAll()
                        // Documentação OpenAPI / Swagger UI.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Console H2 (habilitado apenas no profile 'local').
                        .requestMatchers("/h2-console/**").permitAll()
                        // Demais chamadas exigem autenticação; a autorização fina é por @PreAuthorize.
                        .anyRequest().authenticated())
                // O console H2 renderiza em frames; libera frames da mesma origem.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(securityHandlers)
                        .accessDeniedHandler(securityHandlers))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityHandlers)
                        .accessDeniedHandler(securityHandlers));
        return http.build();
    }

    /**
     * Converte os escopos do JWT em authorities {@code SCOPE_*}, suportando tanto o claim
     * {@code scope} (string separada por espaços) quanto {@code scp} (lista).
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");
        // Aceita tanto "scope" quanto "scp" (padrão de alguns provedores).
        scopes.setAuthoritiesClaimName("scope");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        return converter;
    }
}
