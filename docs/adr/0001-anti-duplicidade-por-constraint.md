# ADR 0001 — Anti-duplicidade garantida por constraint de banco

## Status
Aceito.

## Contexto
`externalId` identifica uma solicitação do sistema de origem. Sistemas de origem fazem retry, e
requisições com o mesmo `externalId` podem chegar quase ao mesmo tempo. Precisamos garantir que
não existam dois registros com o mesmo `externalId`, inclusive sob concorrência.

## Decisão
A unicidade é garantida por uma constraint `UNIQUE (external_id)` no banco — não por uma verificação
prévia na aplicação. O serviço tenta inserir e, se o banco rejeitar por violação de unicidade,
trata o conflito: retry idempotente (mesmo conteúdo) devolve o registro existente com `200`;
conteúdo divergente resulta em `409`.

## Consequências
- Correto sob concorrência: exatamente um insert vence; os demais caem no tratamento de conflito.
- Evita a condição de corrida TOCTOU de "checar e depois inserir".
- Exige tratar `DataIntegrityViolationException` como caminho de negócio esperado, não como erro.

## Alternativas consideradas
- **Checar antes de inserir**: insuficiente sob concorrência (duas requisições podem checar "não
  existe" simultaneamente).
- **Lock distribuído/serialização por `externalId`**: adiciona complexidade e ponto de contenção
  sem benefício sobre a constraint do banco para este caso.
