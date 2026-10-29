# Activation Service

Serviço de ativação de produtos de clientes: registra solicitações, consulta status e integra
com um provedor externo de ativação. Desenvolvido para o desafio técnico Java Pleno/Sênior
(Deroyque IT Consulting Services).

Stack: **Java 21 · Spring Boot 3.3 · Spring MVC · Spring Data JPA/Hibernate · PostgreSQL · Flyway ·
Spring Security (OAuth2 Resource Server) · Maven**.

---

## Sumário

- [Como executar](#como-executar)
- [Arquitetura e fluxo](#arquitetura-e-fluxo)
- [API](#api)
- [Decisões técnicas](#decisões-técnicas)
  - [Duplicidade (item 3)](#duplicidade-item-3)
  - [Integração com o provedor (item 5)](#integração-com-o-provedor-item-5)
  - [Segurança (item 7)](#segurança-item-7)
- [Testes](#testes)
- [Cenário de análise (item 10)](#cenário-de-análise-item-10)
- [Produção](#produção)
- [Microsserviços e cloud](#microsserviços-e-cloud)
- [Pontos pendentes](#pontos-pendentes)
- [Melhorias futuras](#melhorias-futuras)
- [Uso de IA](#uso-de-ia)

---

## Como executar

### Opção A — Docker Compose (app + banco + mock do provedor)

Pré-requisito: Docker.

```bash
docker compose up --build
```

Sobe três contêineres: PostgreSQL, um mock do provedor (WireMock, responde `200` em
`POST /provider/v1/activate`) e a aplicação em `http://localhost:8080`.

Para testar a API ponta a ponta:

```bash
# 1) Obter um token de desenvolvimento (HMAC) com os dois escopos
TOKEN=$(curl -s "http://localhost:8080/dev/token?scope=activation.read%20activation.write" | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# 2) Criar uma solicitação
curl -i -X POST http://localhost:8080/api/v1/requests \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'

# 3) Consultar pelo id devolvido no passo anterior
curl -s http://localhost:8080/api/v1/requests/<ID> -H "Authorization: Bearer $TOKEN"
```

Após alguns segundos o worker processa a solicitação e o status passa de `PENDING` a `COMPLETED`.

### Opção B — Rodar localmente (perfil `local`, banco H2 em memória)

Pré-requisito: JDK 21 e Maven. Não precisa de banco nem do provedor externo.

No **Windows PowerShell** (as aspas são obrigatórias para o PowerShell não quebrar o argumento no ponto):

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

No **Linux/macOS** (bash):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

O emissor de token de desenvolvimento (`/dev/token`) fica disponível, assim como o console H2 em
`/h2-console`.

### Testar pelo Swagger UI

Com a aplicação no ar, abra **http://localhost:8080/swagger-ui.html** (especificação em
`/v3/api-docs`). Para chamar os endpoints protegidos:

1. Obtenha um token em `GET /dev/token` (profile `local`) — copie o `access_token`.
2. Clique em **Authorize** (cadeado), cole o token e confirme.
3. Agora as chamadas enviam `Authorization: Bearer <token>` automaticamente.

### Rodar os testes

```bash
mvn verify
```

- **Testes unitários e de web** rodam sem Docker.
- **Testes de integração** (`*IT`) usam **Testcontainers (PostgreSQL)** e **WireMock** e, portanto,
  exigem Docker em execução. Sem Docker, rode apenas os leves:
  ```bash
  mvn test -Dtest='*Test'
  ```
  No **Windows PowerShell**, coloque o parâmetro entre aspas: `mvn test "-Dtest=*Test"`.

---

## Arquitetura e fluxo

```
Cliente ──POST /requests──▶ ActivationController ──▶ ActivationService ──▶ [DB: PENDING]
                                                                              │
                                   (resposta 201 imediata, processamento assíncrono)
                                                                              ▼
            ActivationWorker (@Scheduled) ──claimBatch (FOR UPDATE SKIP LOCKED)──▶ [DB: PROCESSING]
                                                                              │
                                   ActivationProviderClient ──POST /activate──▶ Provedor externo
                                                                              │
                                   applyOutcome ──▶ [DB: COMPLETED | FAILED | volta a PENDING c/ backoff]
```

Camadas (pacotes sob `com.deroyque.activation`):

| Pacote | Responsabilidade |
|---|---|
| `api` | Controllers, DTOs, tratamento de erros (ProblemDetail), filtro de traceId |
| `service` | Casos de uso (criar/consultar/listar), anti-duplicidade, specifications |
| `domain` | Entidade `ActivationRequest`, máquina de estados, repositório |
| `provider` | Cliente HTTP do provedor, worker de outbox, reconciliação, backoff |
| `security` | Resource Server JWT, escopos, isolamento por consumidor, tokens de dev |

### Máquina de estados

```
PENDING ──▶ PROCESSING ──▶ COMPLETED   (terminal)
                       └─▶ FAILED       (terminal)
PROCESSING ──▶ PENDING                  (falha temporária: reagenda com backoff)
```

As transições são validadas no domínio (`ActivationRequest.transitionTo`), não por setters livres:
um `COMPLETED` nunca volta a `PROCESSING`. A proteção contra escrita concorrente de status usa
**lock otimista** (`@Version`). Uma falha temporária volta a `PENDING` preservando o contador de
tentativas (`attempts`) e agendando a próxima tentativa (`next_attempt_at`).

---

## API

Base: `/api/v1/requests`. Datas em **UTC, ISO-8601**. Autenticação via **Bearer JWT**.

### `POST /api/v1/requests` — cria uma solicitação (escopo `activation.write`)

```json
{ "externalId": "EXT-12345", "customerId": "CUST-10001", "productCode": "INTERNET-500" }
```

- `201 Created` + header `Location` quando cria um novo registro.
- `200 OK` quando é um retry idempotente (mesmo `externalId` e mesmo conteúdo).
- `409 Conflict` quando o `externalId` já existe com conteúdo divergente.
- `400 Bad Request` (ProblemDetail) para dados inválidos.

Resposta:

```json
{
  "id": "0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90",
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500",
  "status": "PENDING",
  "attempts": 0,
  "createdAt": "2026-10-26T14:03:11Z",
  "updatedAt": "2026-10-26T14:03:11Z"
}
```

### `GET /api/v1/requests/{id}` — consulta (escopo `activation.read`)

`200 OK` com a solicitação, ou `404 Not Found` (ProblemDetail) se não existir — ou se pertencer a
outro consumidor (isolamento BOLA).

### `GET /api/v1/requests?status=&customerId=&page=&size=` — listagem paginada (escopo `activation.read`)

Filtros opcionais por `status` e `customerId`; paginação `page`/`size` (teto 100), ordenada por
`createdAt` desc. Sempre restrita ao consumidor autenticado.

Índices de apoio criados na migração: `(status, next_attempt_at)` para o worker, `(customer_id)`
e `(consumer_id)` para a listagem/isolamento, além do índice único de `external_id`.

### Erros (RFC 9457)

Todas as respostas de erro usam `application/problem+json` com um `traceId` para correlação:

```json
{
  "type": "https://api.deroyque.com/problems/validation-error",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos não passaram na validação.",
  "instance": "/api/v1/requests",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [ { "field": "customerId", "message": "é obrigatório" } ]
}
```

---

## Decisões técnicas

### Duplicidade (item 3)

**A garantia real de unicidade é a constraint `UNIQUE (external_id)` no banco.** Verificar antes
de inserir ("existe? então insere") não basta: entre a verificação e o insert, uma segunda
requisição concorrente pode inserir o mesmo `externalId` (condição de corrida TOCTOU).

O fluxo em `ActivationService.create`:

1. Caminho rápido: se já existe, resolve pela semântica abaixo (evita um insert que falharia).
2. Caso contrário, tenta inserir (`saveAndFlush`).
3. Se o banco rejeitar por violação de unicidade (`DataIntegrityViolationException`), significa que
   outra requisição venceu a corrida: buscamos o registro persistido e aplicamos a semântica.

Sob concorrência, **exatamente um insert vence** e os demais caem no tratamento de conflito.

| Cenário | Resposta |
|---|---|
| Mesmo `externalId`, mesmo conteúdo (retry legítimo) | `200 OK` com o registro existente (idempotente) |
| Mesmo `externalId`, conteúdo divergente (`productCode` diferente) | `409 Conflict` (ProblemDetail) |
| Duas requisições idênticas em paralelo | Uma recebe `201`; a outra recebe `200` com o mesmo registro |

Justificativa do `200` no retry: o sistema de origem fez retry de uma operação que já teve sucesso;
devolver o recurso existente é idempotente e evita que o cliente trate como erro. Já um conteúdo
divergente sob o mesmo `externalId` é uma inconsistência do chamador, logo `409`.

### Integração com o provedor (item 5)

**Processamento assíncrono.** O `POST /api/v1/requests` **não** espera o provedor: persiste a
solicitação como `PENDING` e retorna imediatamente. Assim o tempo de resposta da API não depende da
latência (nem da indisponibilidade) do provedor. O processamento fica a cargo de um worker.

**Padrão outbox para durabilidade.** A solicitação é gravada na mesma transação da API. Se a
aplicação cair entre o commit e a chamada ao provedor, o registro continua `PENDING` e será
reprocessado — entrega *at-least-once*. A chamada HTTP **nunca ocorre dentro de uma transação de
banco aberta** (isso seguraria conexões do pool durante I/O de rede); o processamento se divide em
duas transações curtas (marcar `PROCESSING`; aplicar o desfecho) com a chamada HTTP entre elas.
Esses métodos transacionais ficam num bean separado (`ActivationStateUpdater`) para que o proxy
transacional do Spring funcione (evitando o problema de self-invocation).

**Timeouts explícitos.** O cliente HTTP (`RestClient`) tem timeout de conexão e de leitura
configuráveis (`provider.connect-timeout`, `provider.read-timeout`).

**Idempotência.** Enviamos o id da solicitação como `Idempotency-Key` ao provedor. Num timeout não
sabemos se o provedor ativou; ao reenviar com a mesma chave, um provedor idempotente evita ativar o
produto duas vezes.

**Mapeamento de cenários** (`ActivationProviderClient` → `ProviderOutcome`):

| Retorno do provedor | Novo status | Nova tentativa? | Justificativa |
|---|---|---|---|
| Sucesso (2xx) | `COMPLETED` | Não | Ativação concluída |
| HTTP 400 | `FAILED` | Não | Erro de requisição; retry não resolve |
| HTTP 500 | permanece elegível | Sim | Falha do servidor é tipicamente transitória |
| Timeout | permanece elegível | Sim | Resultado indeterminado; idempotência protege o reenvio |
| HTTP 429 / 503 (com `Retry-After`) | permanece elegível | Sim | Indisponibilidade temporária; respeita `Retry-After` |

**Limite de tentativas e backoff.** Até `activation.worker.max-attempts`. A cada falha transitória
a solicitação volta a `PENDING` com `next_attempt_at = agora + backoff`, onde o backoff é
exponencial (`base * 2^(attempts-1)`, com teto). Se o provedor enviou `Retry-After`, ele tem
prioridade. Esgotado o limite, a solicitação vai a `FAILED`.

**Reconciliação.** Um job periódico (`ActivationReconciliationJob`) devolve a `PENDING` as
solicitações presas em `PROCESSING` por mais de `activation.reconciliation.stuck-after` (ex.: a
aplicação caiu após marcar `PROCESSING`).

Sobre resiliência mais elaborada (circuit breaker, jitter): deixei como melhoria futura e explico o
porquê em [Melhorias futuras](#melhorias-futuras) — o enunciado valoriza decisão sobre quantidade
de bibliotecas.

### Segurança (item 7)

A API é um **OAuth2 Resource Server** que **valida JWTs**; não gerencia usuários nem emite tokens
em produção.

- **Fluxo adequado para máquina-a-máquina:** *Client Credentials*. Cada sistema consumidor tem um
  `client_id`/`secret` e obtém um access token do Authorization Server. É o fluxo correto para
  comunicação entre sistemas, sem usuário final no meio.
- **O que o serviço valida no JWT:** assinatura (via **JWKS** do issuer), emissor (`iss`), expiração
  (`exp`) e, quando configurada, a audiência. As chaves públicas vêm do endpoint JWKS do
  Authorization Server e são cacheadas pelo Spring. Em produção isso é ativado pelo profile `prod`
  com `OAUTH2_ISSUER_URI` (RS256).
- **Autorização por escopo:** `activation.write` para criar, `activation.read` para consultar
  (via `@PreAuthorize` nos endpoints; escopos do claim `scope` viram authorities `SCOPE_*`).
- **Isolamento entre consumidores (OWASP API Top 10 – BOLA):** cada registro guarda o `consumerId`
  (claim `azp`/`sub`); consultas e listagens são sempre filtradas pelo consumidor autenticado — um
  sistema não enxerga solicitações criadas por outro.
- **Credencial para chamar o provedor:** passada por variável de ambiente (`PROVIDER_API_KEY`),
  nunca commitada; em produção viria de um gerenciador de segredos e seria rotacionada (ver
  [Produção](#produção)).
- **Limites:** tamanho do corpo da requisição limitado (`server.tomcat.max-http-form-post-size`,
  `max-swallow-size`); rate limiting fica melhor no gateway/API management (ver melhorias).
- **Respostas 401/403** também seguem o formato ProblemDetail (`ProblemDetailSecurityHandlers`).

Para desenvolvimento/testes sem Authorization Server, há um decoder **HMAC (HS256)** ativado apenas
quando `security.dev.jwt.hmac-secret` está presente, e um endpoint `/dev/token` que emite tokens.
Nada disso fica ativo no profile `prod`.

---

## Testes

Estratégia: priorizamos testes que dão confiança para alterar o sistema, não cobertura pela
cobertura.

| Tipo | O quê | Precisa de Docker? |
|---|---|---|
| Unitário (domínio) | `ActivationRequestTest`: máquina de estados, transições inválidas, contagem de tentativas (sem Spring) | Não |
| Unitário (cliente) | `ActivationProviderClientTest`: mapeamento 2xx/400/500/timeout/429 e header de idempotência, via WireMock | Não |
| Web | `ActivationControllerTest` (`@WebMvcTest` + MockMvc): 201/Location, 200 idempotente, 400 validação, 404, 401 sem token, 403 escopo insuficiente | Não |
| Persistência | `ActivationRequestRepositoryTest` (`@DataJpaTest`): unicidade de `externalId` | Não (H2) |
| Integração — concorrência | `ConcurrentDuplicateIT`: 16 threads criando o mesmo `externalId` → exatamente 1 registro, contra PostgreSQL real | Sim (Testcontainers) |
| Integração — fluxo | `ActivationFlowIT`: criação → worker → `COMPLETED`/`FAILED`, com WireMock e Awaitility | Sim (Testcontainers) |

Convenção: classes `*Test` são unitárias/web; `*IT` são de integração (exigem Docker).

---

## Cenário de análise (item 10)

Sintomas: tempo médio da API 300 ms → 2,5 s; algumas consultas 50 ms → 1,2 s; provedor externo
400 ms → 3 s; CPU 45% e memória 60% (sem saturação de recursos).

**1. O que eu investigaria primeiro.** Como CPU e memória estão saudáveis, o gargalo é quase
certamente espera (I/O), não processamento. Começaria olhando as métricas que já expomos
(Micrometer/Prometheus): latência por endpoint, latência e taxa de erro do provedor, e uso do pool
de conexões do banco (HikariCP: conexões ativas, tempo de espera por conexão).

**2. Aplicação × banco × integração.** Usaria o `traceId` (propagado da requisição até a chamada ao
provedor) para seguir requisições lentas ponta a ponta. Se a latência cresce junto com a do
provedor, a origem é a integração; se cresce com o tempo de query e a espera no pool, é o banco. O
tracing distribuído (ver melhorias) tornaria isso imediato.

**3. Logs/métricas úteis.** Timers por endpoint; `http.client` timer do provedor; `hikaricp.*`
(conexões ativas/pendentes, tempo de aquisição); logs lentos do PostgreSQL (`log_min_duration`) e
`EXPLAIN ANALYZE` nas queries suspeitas; contagem de solicitações por status (fila de `PENDING`
crescendo indica worker não vazando).

**4. Melhorias possíveis.** Índices adequados às queries de listagem; aumentar/ajustar o pool
apenas se o banco suportar; e, para a integração, garantir timeouts curtos + circuit breaker para
não deixar threads presas.

**5. (Diferencial) Causa comum.** Hipótese: o endpoint de listagem faz uma query que passou a
varrer a tabela (falta de índice ou filtro novo), o que segura conexões do pool por mais tempo;
com o pool exaurido, até requisições simples esperam por conexão — e, se alguma parte do fluxo
ainda tocasse o provedor de forma síncrona, a lentidão dele amplificaria a espera. Ou seja, **uma
query lenta drenando o pool** explicaria os três sintomas ao mesmo tempo. Confirmaria olhando
`hikaricp.connections.pending` > 0 junto com uma query lenta no `EXPLAIN`.

**6. (Diferencial) Validar a correção.** Comparar as métricas (p95/p99 por endpoint, espera no
pool, query time) antes/depois, idealmente com um teste de carga reproduzindo o tráfego; e observar
em produção com um rollout gradual (canary), revertendo se os percentis não melhorarem.

---

## Produção

- **Como executar.** Imagem Docker (ver `Dockerfile`), profile `prod`, configuração 100% por
  variáveis de ambiente. Rodar atrás de um load balancer, com múltiplas instâncias (o worker é
  seguro para concorrência — ver cloud). Comando efetivo do contêiner:
  `java $JAVA_OPTS -jar app.jar` com `SPRING_PROFILES_ACTIVE=prod`.
- **Imagem.** Build multi-stage, runtime `eclipse-temurin:21-jre-alpine`, **usuário não-root**,
  memória da JVM ajustada ao contêiner (`-XX:MaxRAMPercentage`), **health check** no endpoint de
  readiness.
- **Desligamento gracioso.** `server.shutdown: graceful` — o Boot aguarda requisições em andamento
  antes de encerrar.
- **Observabilidade.** Actuator com *liveness*/*readiness*; métricas Prometheus em
  `/actuator/prometheus`; `traceId` em todas as linhas de log (e propagado à chamada do provedor);
  sem dados sensíveis (tokens/credenciais) nos logs.
- **Banco.** Migrações versionadas com Flyway aplicadas no startup.
- **Segredos.** `OAUTH2_ISSUER_URI`, credenciais do banco e `PROVIDER_API_KEY` vêm do ambiente; em
  produção, de um gerenciador de segredos (AWS Secrets Manager, Vault, etc.) com rotação.

Não foram criados manifests de Kubernetes/Terraform (fora do escopo pedido). Em um ambiente real,
eu empacotaria com um Helm chart (Deployment com probes já expostas, HPA por CPU/latência,
`Secret`/`ExternalSecret` para credenciais) e provisionaria o banco via Terraform.

---

## Microsserviços e cloud

- **Várias instâncias sem quebrar duplicidade nem o processamento.** A unicidade é garantida pela
  constraint no banco (vale para N instâncias). O worker usa
  `SELECT ... FOR UPDATE SKIP LOCKED`, então instâncias concorrentes reservam lotes **disjuntos** de
  `PENDING` — não há processamento duplicado nem necessidade de um "líder".
- **Configuração e segredos.** Variáveis de ambiente; em cloud, um serviço de segredos (Secrets
  Manager/Parameter Store/Vault) injetado no contêiner.
- **Notificar outros microsserviços de uma ativação concluída.** Publicar um evento
  (`ActivationCompleted`) em uma fila/tópico (SNS/SQS, Kafka, RabbitMQ). Para não perder eventos, o
  ideal é estender o padrão outbox também à publicação (gravar o evento na mesma transação do
  `COMPLETED` e publicá-lo com um relay) — ver melhorias.
- **CI/CD até o deploy.** `build → testes (unit + IT com Testcontainers) → imagem Docker →
  publicação no registry → deploy (canary)`. O workflow em `.github/workflows/ci.yml` já cobre
  build e testes.

---

## Pontos pendentes

- **Circuit breaker e jitter no backoff** não foram implementados (descritos em melhorias). Hoje há
  retry com backoff exponencial e teto, além de timeouts — suficiente para o escopo.
- **Rate limiting** não implementado na aplicação; a recomendação é fazê-lo no gateway/API
  management. Há apenas limite de tamanho de corpo.
- **Publicação de eventos** de conclusão (para outros serviços) está descrita, não implementada.
- **Testes de integração exigem Docker**; neste ambiente de desenvolvimento o Docker não estava
  disponível, então foram validados por compilação. Os testes unitários/web (22) passam
  (`mvn test -Dtest='*Test'`).

## Melhorias futuras

- **Resilience4j**: circuit breaker no cliente do provedor (abre após uma taxa de erro, evitando
  martelar um provedor em pane) e **jitter** no backoff (evita efeito manada de retries
  sincronizados).
- **Outbox de eventos** para publicação confiável de `ActivationCompleted` em fila/tópico.
- **Documentação OpenAPI** (springdoc) servida pela aplicação.
- **ArchUnit** para impor regras de dependência entre camadas.
- **Tracing distribuído** (OpenTelemetry) e **logs estruturados em JSON** para correlação ponta a
  ponta em produção.
- **Métrica de negócio**: idade da fila de `PENDING` e tempo até `COMPLETED` como SLIs.

## Uso de IA

Este projeto foi desenvolvido com o apoio de um assistente de IA (Kiro) na geração do código, dos
testes e desta documentação, com revisão e decisões de arquitetura conduzidas ao longo do processo.
As decisões técnicas registradas aqui refletem as escolhas adotadas intencionalmente, não apenas
sugestões automáticas.
