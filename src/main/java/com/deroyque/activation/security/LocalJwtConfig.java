package com.deroyque.activation.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Decoder/encoder JWT baseado em segredo HMAC (HS256) para desenvolvimento e testes locais,
 * ativado apenas quando {@code security.dev.jwt.hmac-secret} está presente.
 *
 * <p>Em produção esta configuração fica inativa: o {@code issuer-uri}/{@code jwk-set-uri} do
 * {@code application.yml} fazem o Spring validar tokens assinados pelo Authorization Server real
 * (RS256/JWKS). Assim o código de produção não depende de segredo simétrico.
 */
@Configuration
@ConditionalOnProperty(name = "security.dev.jwt.hmac-secret")
public class LocalJwtConfig {

    private static final String ALG = "HmacSHA256";

    private final SecretKeySpec key;

    public LocalJwtConfig(org.springframework.core.env.Environment env) {
        String secret = env.getProperty("security.dev.jwt.hmac-secret", "");
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALG);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
