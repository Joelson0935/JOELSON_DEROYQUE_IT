package com.deroyque.activation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Expõe a identidade do consumidor (client) autenticado via JWT.
 *
 * <p>Para comunicação máquina-a-máquina (client credentials), o identificador do consumidor
 * costuma estar no claim {@code azp} (authorized party) ou, na ausência dele, no {@code sub}.
 * Esse valor é gravado em {@code consumerId} e usado para isolamento entre consumidores.
 */
@Component
public class ConsumerContext {

    /** Retorna o identificador do consumidor autenticado, ou {@code null} se não houver JWT. */
    public String currentConsumerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String azp = jwt.getClaimAsString("azp");
        return (azp != null && !azp.isBlank()) ? azp : jwt.getSubject();
    }
}
