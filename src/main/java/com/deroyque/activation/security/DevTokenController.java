package com.deroyque.activation.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emite tokens JWT de desenvolvimento (HMAC) para testar a API manualmente sem um Authorization
 * Server real. Disponível APENAS no profile {@code local}.
 *
 * <p>Fica ativo apenas quando há um {@link JwtEncoder} no contexto — ou seja, quando o
 * {@code LocalJwtConfig} (HMAC de dev) está presente. Em produção (RS256 via issuer), este bean
 * não existe e o endpoint não é exposto.
 *
 * <p>Exemplo:
 * <pre>GET /dev/token?scope=activation.read activation.write&amp;azp=cliente-a</pre>
 */
@RestController
@ConditionalOnBean(JwtEncoder.class)
public class DevTokenController {

    private final JwtEncoder jwtEncoder;

    public DevTokenController(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @GetMapping("/dev/token")
    public Map<String, String> token(
            @RequestParam(defaultValue = "activation.read activation.write") String scope,
            @RequestParam(defaultValue = "dev-client") String azp) {

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("dev")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject(azp)
                .claim("azp", azp)
                .claim("scope", scope)
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return Map.of("access_token", value, "token_type", "Bearer", "scope", scope);
    }
}
