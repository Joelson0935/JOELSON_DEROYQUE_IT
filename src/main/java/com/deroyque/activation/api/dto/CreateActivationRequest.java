package com.deroyque.activation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de entrada para criação de uma solicitação de ativação.
 *
 * <p>DTO separado da entidade JPA: a API expõe este contrato estável, e a persistência
 * pode evoluir sem impactar os consumidores. Validações declaradas via Bean Validation.
 */
public record CreateActivationRequest(

        @NotBlank(message = "é obrigatório")
        @Size(max = 100, message = "deve ter no máximo 100 caracteres")
        String externalId,

        @NotBlank(message = "é obrigatório")
        @Size(max = 100, message = "deve ter no máximo 100 caracteres")
        String customerId,

        @NotBlank(message = "é obrigatório")
        @Size(max = 100, message = "deve ter no máximo 100 caracteres")
        String productCode
) {
}
