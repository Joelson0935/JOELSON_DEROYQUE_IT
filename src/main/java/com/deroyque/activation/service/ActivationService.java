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

/**
 * Casos de uso de criação e consulta de solicitações de ativação.
 *
 * <p><b>Estratégia anti-duplicidade.</b> Verificar no banco se o externalId existe e só então
 * inserir NÃO é suficiente: entre a verificação e o insert, uma segunda requisição concorrente
 * pode inserir o mesmo externalId (condição de corrida TOCTOU). Por isso a garantia real é a
 * <i>unique constraint</i> no banco. O fluxo é:
 * <ol>
 *   <li>Tentar inserir diretamente.</li>
 *   <li>Se o banco rejeitar por violação de unicidade, significa que já existe um registro —
 *       então buscamos o existente e decidimos a semântica da resposta:</li>
 *   <li>mesmo conteúdo (retry legítimo) → devolve o existente com HTTP 200 (idempotente);</li>
 *   <li>conteúdo divergente → {@link DuplicateExternalIdException} → HTTP 409.</li>
 * </ol>
 * Esse padrão (tentar-inserir-e-tratar-conflito) é correto também sob concorrência: exatamente
 * um insert vence; os demais caem no ramo de conflito.
 */
@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final ActivationRequestRepository repository;

    public ActivationService(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreateActivationResult create(CreateActivationRequest command, String consumerId) {
        // Caminho rápido: se já existe, evita tentar um insert que sabidamente falharia.
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
            // Corrida: outra requisição inseriu o mesmo externalId entre o findByExternalId e o flush.
            log.info("Conflito de unicidade em externalId={}; tratando como retry/duplicata",
                    command.externalId());
            var persisted = repository.findByExternalId(command.externalId())
                    .orElseThrow(() -> e); // se nem agora existe, propaga o erro original
            return resolveExisting(persisted, command);
        }
    }

    /**
     * Decide a semântica para um externalId já existente: retry idempotente (mesmo conteúdo)
     * devolve o registro; conteúdo divergente é conflito.
     */
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

    /**
     * Busca por id restrita a um consumidor (isolamento BOLA): se o registro pertencer a outro
     * consumidor, comporta-se como "não encontrado" (não vaza existência de dados alheios).
     */
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
