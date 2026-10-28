# ADR 0002 — Processamento assíncrono do provedor com padrão outbox

## Status
Aceito.

## Contexto
Após criar uma solicitação, é preciso chamar o provedor externo de ativação. Essa chamada é I/O de
rede, podendo ser lenta ou falhar. Precisamos de bom tempo de resposta na API, durabilidade (não
perder solicitações) e não ativar o produto duas vezes em caso de reenvio.

## Decisão
O `POST /api/v1/requests` apenas persiste a solicitação como `PENDING` e retorna. Um worker
agendado (`ActivationWorker`) consome o outbox: reserva um lote com `FOR UPDATE SKIP LOCKED`, marca
`PROCESSING`, chama o provedor **fora** de qualquer transação de banco e aplica o desfecho em uma
transação curta. Enviamos o id da solicitação como `Idempotency-Key`. Falhas transitórias voltam a
`PENDING` com backoff exponencial (teto + prioridade ao `Retry-After`); esgotado o limite de
tentativas, vão a `FAILED`. Um job de reconciliação resgata registros presos em `PROCESSING`.

## Consequências
- Tempo de resposta da API independe da latência/disponibilidade do provedor.
- Durabilidade *at-least-once*: se a app cair, o registro continua `PENDING` e é reprocessado.
- Conexões do pool não ficam presas durante I/O de rede.
- Múltiplas instâncias processam em paralelo sem duplicar (SKIP LOCKED), sem necessidade de líder.
- Exige idempotência no provedor para o reenvio não ativar duas vezes.
- Os métodos transacionais ficam num bean separado (`ActivationStateUpdater`) para o proxy do
  Spring funcionar (evita self-invocation).

## Alternativas consideradas
- **Chamada síncrona no POST**: tempo de resposta refém do provedor; risco de esgotar threads sob
  lentidão do provedor.
- **Fila externa (SQS/Kafka) desde já**: mais robusto para escala, porém adiciona infraestrutura;
  o outbox no próprio banco atende ao escopo e é um passo natural para evoluir à fila depois.
