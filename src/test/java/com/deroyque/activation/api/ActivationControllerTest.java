package com.deroyque.activation.api;

import com.deroyque.activation.domain.ActivationRequest;
import com.deroyque.activation.security.ConsumerContext;
import com.deroyque.activation.security.ProblemDetailSecurityHandlers;
import com.deroyque.activation.security.SecurityConfig;
import com.deroyque.activation.service.ActivationService;
import com.deroyque.activation.service.CreateActivationResult;
import com.deroyque.activation.service.RequestNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivationController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandlers.class, ConsumerContext.class})
class ActivationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivationService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    private ActivationRequest sample() {
        return ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", "cliente-a");
    }

    @Test
    void criaComDadosValidosRetorna201ComLocation() throws Exception {
        var entity = sample();
        when(service.create(any(), any())).thenReturn(CreateActivationResult.created(entity));

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_activation.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryIdempotenteRetorna200() throws Exception {
        var entity = sample();
        when(service.create(any(), any())).thenReturn(CreateActivationResult.existing(entity));

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_activation.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void dadosInvalidosRetorna400ComProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_activation.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId":"","customerId":"CUST-1","productCode":"INTERNET-500"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors[0].field").value("externalId"));
    }

    @Test
    void consultaExistenteRetorna200() throws Exception {
        var entity = sample();
        when(service.getByIdForConsumer(any(), any())).thenReturn(entity);

        mockMvc.perform(get("/api/v1/requests/{id}", entity.getId())
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_activation.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-1"));
    }

    @Test
    void consultaInexistenteRetorna404() throws Exception {
        var id = UUID.randomUUID();
        when(service.getByIdForConsumer(eq(id), any())).thenThrow(new RequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id)
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_activation.read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escopoInsuficienteRetorna403() throws Exception {
        
        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_activation.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}
                                """))
                .andExpect(status().isForbidden());
    }
}
