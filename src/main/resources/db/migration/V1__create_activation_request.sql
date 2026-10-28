-- Tabela principal de solicitações de ativação.
CREATE TABLE activation_request (
    id             UUID         NOT NULL,
    external_id    VARCHAR(100) NOT NULL,
    customer_id    VARCHAR(100) NOT NULL,
    product_code   VARCHAR(100) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    failure_reason  VARCHAR(500),
    consumer_id     VARCHAR(150),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activation_request PRIMARY KEY (id)
);

-- Garantia de unicidade de externalId: base da estratégia anti-duplicidade.
-- É esta constraint (e não uma verificação prévia na aplicação) que impede
-- dois registros com o mesmo externalId mesmo sob requisições concorrentes.
ALTER TABLE activation_request
    ADD CONSTRAINT uk_activation_request_external_id UNIQUE (external_id);

-- Índice para o worker de outbox (busca PENDING elegíveis por next_attempt_at).
CREATE INDEX idx_activation_request_status_next_attempt
    ON activation_request (status, next_attempt_at);

-- Índices para a listagem paginada com filtros (status, customerId).
CREATE INDEX idx_activation_request_customer
    ON activation_request (customer_id);

-- Índice de apoio ao isolamento por consumidor.
CREATE INDEX idx_activation_request_consumer
    ON activation_request (consumer_id);
