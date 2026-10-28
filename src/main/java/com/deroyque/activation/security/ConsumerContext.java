package com.deroyque.activation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ConsumerContext {

    public String currentConsumerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String azp = jwt.getClaimAsString("azp");
        return (azp != null && !azp.isBlank()) ? azp : jwt.getSubject();
    }
}
