package com.deroyque.activation.service;

import com.deroyque.activation.api.dto.CreateActivationRequest;
import com.deroyque.activation.domain.ActivationRequest;
import com.deroyque.activation.domain.ActivationRequestRepository;
import com.deroyque.activation.domain.RequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final ActivationRequestRepository repository;

    public ActivationService(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreateActivationResult create(CreateActivationRequest command, String consumerId) {
        
        var existing = repository.findByExternalId(command.externalId());
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), command);
        }

        var entity = ActivationRequest.create(
                command.externalId(), command.customerId(), command.productCode(), consumerId);
        try {
            var saved = repository.saveAndFlush(entity);
            log.info("Solicitação criada id={} externalId={}", saved.getId(), saved.getExternalId());
            return CreateActivationResult.created(saved);
        } catch (DataIntegrityViolationException e) {
            
            log.info("Conflito de unicidade em externalId={}; tratando como retry/duplicata",
                    command.externalId());
            var persisted = repository.findByExternalId(command.externalId())
                    .orElseThrow(() -> e); 
            return resolveExisting(persisted, command);
        }
    }

    private CreateActivationResult resolveExisting(ActivationRequest existing, CreateActivationRequest command) {
        boolean sameContent = existing.getCustomerId().equals(command.customerId())
                && existing.getProductCode().equals(command.productCode());
        if (sameContent) {
            return CreateActivationResult.existing(existing);
        }
        throw new DuplicateExternalIdException(command.externalId());
    }

    @Transactional(readOnly = true)
    public ActivationRequest getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public ActivationRequest getByIdForConsumer(UUID id, String consumerId) {
        var request = repository.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
        if (consumerId != null && request.getConsumerId() != null
                && !consumerId.equals(request.getConsumerId())) {
            throw new RequestNotFoundException(id);
        }
        return request;
    }

    @Transactional(readOnly = true)
    public Page<ActivationRequest> list(RequestStatus status, String customerId,
                                        String consumerId, Pageable pageable) {
        var spec = ActivationRequestSpecifications.withFilters(status, customerId, consumerId);
        return repository.findAll(spec, pageable);
    }
}
