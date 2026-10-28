package com.deroyque.activation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Faz com que falhas de autenticação (401) e de autorização (403) ocorridas na cadeia de filtros
 * de segurança respondam no mesmo formato Problem Details (RFC 9457) usado pelo resto da API.
 */
@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String PROBLEM_BASE = "https://api.deroyque.com/problems/";

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException authException)
            throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized", "Não autenticado",
                "Credenciais ausentes ou inválidas.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException accessDeniedException)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Acesso negado",
                "O token não possui o escopo necessário para esta operação.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String type, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", MDC.get("traceId"));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
