package com.deroyque.activation.api;

import com.deroyque.activation.api.dto.ActivationResponse;
import com.deroyque.activation.api.dto.CreateActivationRequest;
import com.deroyque.activation.api.dto.PageResponse;
import com.deroyque.activation.domain.RequestStatus;
import com.deroyque.activation.security.ConsumerContext;
import com.deroyque.activation.service.ActivationService;
import com.deroyque.activation.service.CreateActivationResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Endpoints REST de solicitações de ativação.
 *
 * <p>Autorização por escopo (OAuth2):
 * <ul>
 *   <li>criação exige escopo {@code activation.write};</li>
 *   <li>consulta/listagem exigem escopo {@code activation.read}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/requests")
public class ActivationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActivationService service;
    private final ConsumerContext consumerContext;

    public ActivationController(ActivationService service, ConsumerContext consumerContext) {
        this.service = service;
        this.consumerContext = consumerContext;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_activation.write')")
    public ResponseEntity<ActivationResponse> create(
            @Valid @RequestBody CreateActivationRequest request,
            UriComponentsBuilder uriBuilder) {

        CreateActivationResult result = service.create(request, consumerContext.currentConsumerId());
        ActivationResponse body = ActivationResponse.from(result.request());

        if (result.created()) {
            URI location = uriBuilder.path("/api/v1/requests/{id}")
                    .buildAndExpand(result.request().getId())
                    .toUri();
            return ResponseEntity.created(location).body(body);
        }
        // Retry idempotente: registro já existia com o mesmo conteúdo.
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_activation.read')")
    public ActivationResponse getById(@PathVariable UUID id) {
        var request = service.getByIdForConsumer(id, consumerContext.currentConsumerId());
        return ActivationResponse.from(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_activation.read')")
    public PageResponse<ActivationResponse> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        var result = service.list(status, customerId, consumerContext.currentConsumerId(), pageable);
        return PageResponse.from(result, ActivationResponse::from);
    }
}
